package com.nspace.mediacenter.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.nspace.mediacenter.R;
import com.nspace.mediacenter.util.AppIntegrity;
import com.nspace.mediacenter.util.AuthorizationCache;
import com.nspace.mediacenter.util.DeviceId;
import com.nspace.mediacenter.util.RemoteAuthorizer;

/**
 * Host activity for NSpace.
 *
 * <p>Matches the real Metax app structure: full-screen immersive home with
 * settings gear, and browser view with left sidebar navigation.
 * Swaps between HomeFragment and BrowserFragment (and others via MainNavigator).
 */
public final class MainActivity extends AppCompatActivity implements MainNavigator {

  private boolean mLocked = false;

  // ── Lock-screen authorization polling ──────────────────────────
  // When the device is unauthorized, a background poller checks the remote
  // authorization endpoint every 60 seconds so the device auto-unlocks the
  // moment the supplier publishes aid/<id>.enc — without requiring an app
  // restart.
  private static final long POLL_INTERVAL_MS = 60_000L;
  private final Handler pollHandler = new Handler(Looper.getMainLooper());
  private Runnable authPollRunnable;
  private String pendingDeviceId;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    // Integrity gate: a repackaged (re-signed) or debugged binary is rejected
    // before any UI or binding logic runs.
    if (!AppIntegrity.verify(this)) {
      mLocked = true;
      setContentView(R.layout.activity_lock);
      enableImmersiveMode();
      return;
    }

    // ANDROID_ID-based remote authorization gate. The app fetches
    // "<AID_REMOTE_URL>/<android_id>.enc", decrypts it with the shared key, and
    // only enters the home UI when the file both exists and decrypts to this
    // device's own ID. Publishing the file authorizes the device; deleting it
    // revokes. A device that was never authorized and is offline stays locked.
    mLocked = true;
    setContentView(R.layout.activity_verifying);
    enableImmersiveMode();

    final String deviceId = DeviceId.getDeviceId(this);
    RemoteAuthorizer.verify(this, result -> {
      switch (result) {
        case PASS:
          AuthorizationCache.grant(this, deviceId);
          enterMain();
          break;
        case REVOKED:
          AuthorizationCache.revoke(this);
          showLockScreen(deviceId);
          break;
        case OFFLINE:
          if (AuthorizationCache.hasGrant(this, deviceId)) {
            enterMain();
          } else {
            showLockScreen(deviceId);
          }
          break;
      }
    });
  }

  /** Swap the verification screen for the main UI (authorization granted). */
  private void enterMain() {
    stopAuthPolling();
    mLocked = false;
    setContentView(R.layout.activity_main);
    handleLaunchIntent(getIntent());
    if (getSupportFragmentManager().findFragmentById(R.id.content_frame) == null) {
      goHome();
    }
    enableImmersiveMode();
  }

  /** Replace whatever is on screen with the lock screen (unauthorized). */
  private void showLockScreen() {
    showLockScreen(null);
  }

  /**
   * Lock screen for an authorization failure. When {@code deviceId} is provided
   * it is shown in a dedicated line below the supplier-contact prompt, so the
   * supplier can copy the exact value to publish the right
   * {@code aid/<id>.enc} on GitHub Pages to authorize this device.
   */
  private void showLockScreen(String deviceId) {
    mLocked = true;
    setContentView(R.layout.activity_lock);
    TextView title = findViewById(R.id.lock_title_text);
    TextView msg = findViewById(R.id.lock_message_text);
    TextView deviceIdView = findViewById(R.id.lock_device_id_text);
    if (title != null) {
      title.setText(R.string.lock_title);
    }
    if (msg != null) {
      msg.setText(R.string.lock_message);
    }
    if (deviceIdView != null) {
      if (deviceId != null && !deviceId.isEmpty()) {
        // Show the bare device number only (no "Device ID" label), so the
        // supplier can copy the exact value to publish aid/<id>.enc.
        deviceIdView.setText(deviceId);
        deviceIdView.setVisibility(View.VISIBLE);
      } else {
        deviceIdView.setVisibility(View.GONE);
      }
    }
    enableImmersiveMode();
    // Start background polling so the device auto-unlocks once the supplier
    // publishes aid/<id>.enc — no app restart needed.
    startAuthPolling(deviceId);
  }

  /**
   * Start a 60-second background poll of the remote authorization endpoint.
   * On {@code PASS} the device is granted and the main UI is entered; on
   * {@code REVOKED} or {@code OFFLINE} the poller keeps trying.
   */
  private void startAuthPolling(String deviceId) {
    stopAuthPolling();
    pendingDeviceId = deviceId;
    authPollRunnable = new Runnable() {
      @Override
      public void run() {
        if (!mLocked || pendingDeviceId == null) {
          return;
        }
        RemoteAuthorizer.verify(MainActivity.this, result -> {
          if (!mLocked) {
            return; // already unlocked by another path
          }
          switch (result) {
            case PASS:
              AuthorizationCache.grant(MainActivity.this, pendingDeviceId);
              enterMain();
              break;
            case REVOKED:
            case OFFLINE:
              // Keep polling — the supplier may not have published yet.
              pollHandler.postDelayed(authPollRunnable, POLL_INTERVAL_MS);
              break;
          }
        });
      }
    };
    // First check after a short delay (don't hammer the endpoint immediately
    // after the initial check that just failed), then every 60 seconds.
    pollHandler.postDelayed(authPollRunnable, POLL_INTERVAL_MS);
  }

  /** Stop the background authorization poller. */
  private void stopAuthPolling() {
    if (authPollRunnable != null) {
      pollHandler.removeCallbacks(authPollRunnable);
      authPollRunnable = null;
    }
    pendingDeviceId = null;
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    if (mLocked) {
      return;
    }
    handleLaunchIntent(intent);
  }

  @Override
  protected void onDestroy() {
    stopAuthPolling();
    super.onDestroy();
  }

  /**
   * Opens a URL passed via the "url" intent extra. Handy for external
   * deep-links and adb testing, e.g.
   * {@code am start -n com.nspace.mediacenter/.ui.MainActivity -e url "https://..."}.
   */
  private void handleLaunchIntent(Intent intent) {
    if (intent == null) {
      return;
    }
    String url = intent.getStringExtra("url");
    if (url != null && !url.isEmpty()) {
      openUrl(url);
    }
  }

  /**
   * Immersive status bar (hidden, re-shown on a swipe via sticky) while keeping
   * the system navigation/control bar visible. Content is laid out under the
   * status bar but above the navigation bar so the bottom controls stay usable.
   */
  private void enableImmersiveMode() {
    final View decor = getWindow().getDecorView();
    decor.setSystemUiVisibility(
        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
  }

  @Override
  public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    if (hasFocus) {
      enableImmersiveMode();
    }
  }

  @Override
  public void onBackPressed() {
    if (mLocked) {
      return;
    }
    Fragment current = getSupportFragmentManager().findFragmentById(R.id.content_frame);
    if (current instanceof BrowserFragment && ((BrowserFragment) current).canGoBack()) {
      ((BrowserFragment) current).goBack();
      return;
    }
    // If on any screen other than home, go home; otherwise exit
    if (!(current instanceof HomeFragment)) {
      goHome();
      return;
    }
    super.onBackPressed();
  }

  private void showFragment(@NonNull Fragment fragment) {
    getSupportFragmentManager()
        .beginTransaction()
        .replace(R.id.content_frame, fragment)
        .commit();
  }

  // ── MainNavigator implementation ──────────────────────────────

  @Override
  public void openUrl(String url) {
    // Note: deliberately NOT held as an activity field — the fragment manager
    // owns the instance. A long-lived activity reference would keep the old
    // BrowserFragment (and anything it retains) reachable after replace().
    BrowserFragment fragment = new BrowserFragment();
    Bundle args = new Bundle();
    args.putString(BrowserFragment.ARG_URL, url);
    fragment.setArguments(args);
    showFragment(fragment);
  }

  @Override
  public void goHome() {
    showFragment(new HomeFragment());
  }

  @Override
  public void goBookmarks() {
    showFragment(new BookmarkFragment());
  }

  @Override
  public void goHistory() {
    showFragment(new HistoryFragment());
  }

  @Override
  public void goDownloads() {
    showFragment(new DownloadFragment());
  }

  @Override
  public void goPrivacy() {
    showFragment(new PrivacyFragment());
  }

  @Override
  public void goSettings() {
    showFragment(new SettingsFragment());
  }

  @Override
  public void goAccount() {
    showFragment(new AccountFragment());
  }
}

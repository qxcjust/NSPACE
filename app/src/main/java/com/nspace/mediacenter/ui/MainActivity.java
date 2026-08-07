package com.nspace.mediacenter.ui;

import android.content.Intent;
import android.os.Bundle;
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
          showLockScreen();
          break;
        case OFFLINE:
          if (AuthorizationCache.hasGrant(this, deviceId)) {
            enterMain();
          } else {
            showLockScreen();
          }
          break;
      }
    });
  }

  /** Swap the verification screen for the main UI (authorization granted). */
  private void enterMain() {
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
    mLocked = true;
    setContentView(R.layout.activity_lock);
    TextView title = findViewById(R.id.lock_title_text);
    TextView msg = findViewById(R.id.lock_message_text);
    if (title != null) {
      title.setText(R.string.lock_title);
    }
    if (msg != null) {
      msg.setText(R.string.lock_message);
    }
    enableImmersiveMode();
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    if (mLocked) {
      return;
    }
    handleLaunchIntent(intent);
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

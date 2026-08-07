package com.nspace.mediacenter.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.nspace.mediacenter.R;
import com.nspace.mediacenter.util.AppIntegrity;
import com.nspace.mediacenter.util.AuthorizationCache;
import com.nspace.mediacenter.util.DeviceBinder;
import com.nspace.mediacenter.util.VinReader;
import com.nspace.mediacenter.util.VinRemoteChecker;

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

    // Device binding: the config may either bind the app to one specific car
    // VIN (hash present) or run unbound (empty hash). Either way we resolve the
    // concrete binding result here.
    DeviceBinder.Result binding = DeviceBinder.check(this);
    if (binding == DeviceBinder.Result.LOCKED_MISMATCH
        || binding == DeviceBinder.Result.LOCKED_UNREADABLE) {
      // Bound mode, and this unit is not the bound car (or its VIN is
      // unreadable). Hard stop before any UI is shown.
      mLocked = true;
      setContentView(R.layout.activity_lock);
      enableImmersiveMode();
      return;
    }

    if (binding == DeviceBinder.Result.NO_BINDING) {
      // Unbound mode: any car may install, but it is only authorized once its
      // VIN file is published on the remote allowlist (GitHub Pages). Block the
      // UI behind a verification screen and wait for the remote check to decide.
      mLocked = true;
      setContentView(R.layout.activity_verifying);
      enableImmersiveMode();
      startRemoteAuthorizationCheck();
      return;
    }

    // Bound mode and this unit matches: go straight in, with a fire-and-forget
    // revocation check that may escalate to a lock screen later.
    mLocked = false;
    setContentView(R.layout.activity_main);

    handleLaunchIntent(getIntent());
    if (savedInstanceState == null
        && getSupportFragmentManager().findFragmentById(R.id.content_frame) == null) {
      goHome();
    }

    enableImmersiveMode();

    startRemoteRevocationCheck();
  }

  /**
   * Unbound ("publish the VIN to authorize") mode: block entry until the remote
   * allowlist check resolves.
   * <ul>
   *   <li>{@code PASS}    – the car's VIN file is published and matches → enter.</li>
   *   <li>{@code REVOKED} – no file published (404) → lock as unauthorized.</li>
   *   <li>{@code OFFLINE} – can't verify; let a car that previously passed an
   *                         online check in (so a legit car isn't locked in a
   *                         tunnel), but lock a car that was never authorized.</li>
   * </ul>
   */
  private void startRemoteAuthorizationCheck() {
    VinRemoteChecker.verify(result -> {
      switch (result) {
        case PASS:
          AuthorizationCache.grant(this, VinReader.getVin());
          enterMain();
          break;
        case REVOKED:
          AuthorizationCache.revoke(this);
          showLockScreen();
          break;
        case OFFLINE:
          if (AuthorizationCache.hasGrant(this, VinReader.getVin())) {
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

  /** Replace whatever is on screen with the lock screen. */
  private void showLockScreen() {
    mLocked = true;
    setContentView(R.layout.activity_lock);
    enableImmersiveMode();
  }

  private void startRemoteRevocationCheck() {
    VinRemoteChecker.verify(result -> {
      if (result == VinRemoteChecker.Result.REVOKED) {
        mLocked = true;
        Fragment f = getSupportFragmentManager().findFragmentById(R.id.content_frame);
        if (f != null) {
          getSupportFragmentManager().beginTransaction().remove(f).commitNow();
        }
        setContentView(R.layout.activity_lock);
        enableImmersiveMode();
      }
    });
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

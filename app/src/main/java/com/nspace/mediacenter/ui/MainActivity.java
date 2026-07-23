package com.nspace.mediacenter.ui;

import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.nspace.mediacenter.R;

/**
 * Host activity for NSpace.
 *
 * <p>Matches the real Metax app structure: full-screen immersive home with
 * settings gear, and browser view with left sidebar navigation.
 * Swaps between HomeFragment and BrowserFragment (and others via MainNavigator).
 */
public final class MainActivity extends AppCompatActivity implements MainNavigator {

  private BrowserFragment browserFragment;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    if (savedInstanceState == null) {
      goHome();
    }

    enableImmersiveMode();
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
    browserFragment = new BrowserFragment();
    Bundle args = new Bundle();
    args.putString(BrowserFragment.ARG_URL, url);
    browserFragment.setArguments(args);
    showFragment(browserFragment);
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

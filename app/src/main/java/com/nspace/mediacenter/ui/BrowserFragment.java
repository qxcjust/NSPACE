package com.nspace.mediacenter.ui;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.PixelCopy;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.nspace.mediacenter.R;
import com.nspace.mediacenter.core.HistoryManager;
import com.nspace.mediacenter.core.RecentsManager;
import java.io.File;
import java.io.FileOutputStream;
import java.util.UUID;

/**
 * In-app browser surface matching the real Metax app's browser view.
 *
 * <p>Observed on RK3588 car head unit (uiautomator dump):
 * <ul>
 *   <li>Pure black background</li>
 *   <li>Left sidebar navigation bar: Back, Forward, Refresh, Home icons
 *       (vertical, white line-style icons on near-black)</li>
 *   <li>WebView fills remaining space to the right</li>
 *   <li>No top toolbar or visible address bar</li>
 * </ul>
 */
public final class BrowserFragment extends Fragment {

  public static final String ARG_URL = "arg_url";

  private WebView webView;
  private ProgressBar progressBar;

  // Throttle consecutive snapshots of the same URL (e.g. reloads / back-forward)
  private String lastCaptureUrl;
  private long lastCaptureTime;

  @Nullable
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_browser, container, false);
  }

  @SuppressLint("SetJavaScriptEnabled")
  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    webView = view.findViewById(R.id.webview);
    progressBar = view.findViewById(R.id.progress_bar);

    // Left sidebar navigation buttons
    ImageButton btnBack = view.findViewById(R.id.nav_back);
    ImageButton btnForward = view.findViewById(R.id.nav_forward);
    ImageButton btnRefresh = view.findViewById(R.id.nav_refresh);
    ImageButton btnHome = view.findViewById(R.id.nav_home);

    btnBack.setOnClickListener(v -> goBack());
    btnForward.setOnClickListener(v -> {
      if (webView != null && webView.canGoForward()) {
        webView.goForward();
      }
    });
    btnRefresh.setOnClickListener(v -> {
      if (webView != null) {
        webView.reload();
      }
    });
    btnHome.setOnClickListener(v -> {
      if (getActivity() instanceof MainNavigator) {
        ((MainNavigator) getActivity()).goHome();
      }
    });

    // Configure WebView
    webView.getSettings().setJavaScriptEnabled(true);
    webView.getSettings().setDomStorageEnabled(true);
    webView.setWebViewClient(new WebViewClient() {
      @Override
      public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);
        String title = view.getTitle();
        HistoryManager.getInstance().addVisit(title == null ? url : title, url);
        // Update forward button state
        btnForward.setAlpha(view.canGoForward() ? 1f : 0.3f);
        // Capture a snapshot so the home screen can offer "Continue Playing"
        scheduleCapture(title == null ? url : title, url);
      }
    });
    webView.setWebChromeClient(new WebChromeClient() {
      @Override
      public void onProgressChanged(WebView view, int newProgress) {
        progressBar.setProgress(newProgress);
        progressBar.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
      }
    });

    // Load initial URL
    String url = null;
    if (getArguments() != null) {
      url = getArguments().getString(ARG_URL);
    }
    if (url == null || url.isEmpty()) {
      url = "about:blank";
    }
    loadUrl(url);

    // Initial state for forward button
    btnForward.setAlpha(0.3f);
  }

  /**
   * Schedules a snapshot of the current page for the "Continue Playing" row,
   * throttling repeated loads of the same URL so reloads/back-forward don't
   * spam the recents store. The actual capture runs after a short delay to let
   * the page render.
   */
  private void scheduleCapture(String title, String url) {
    if (url == null || url.isEmpty()) {
      return;
    }
    long now = System.currentTimeMillis();
    if (url.equals(lastCaptureUrl) && now - lastCaptureTime < 5000) {
      return;
    }
    lastCaptureUrl = url;
    lastCaptureTime = now;
    final String pageTitle = title;
    webView.postDelayed(() -> captureSnapshot(pageTitle, url), 1200);
  }

  /**
   * Captures the WebView surface to a bitmap and persists it as a recents entry.
   * Prefers {@link PixelCopy} (Android O+) and falls back to a software
   * {@link Canvas} draw on older devices.
   */
  private void captureSnapshot(String title, String url) {
    if (getContext() == null || webView == null || !isAdded()) {
      return;
    }
    final int w = webView.getWidth();
    final int h = webView.getHeight();
    if (w <= 0 || h <= 0) {
      return;
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      try {
        final Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        final Rect srcRect = new Rect();
        webView.getGlobalVisibleRect(srcRect);
        PixelCopy.request(getActivity().getWindow(), srcRect, bitmap,
            copyResult -> {
              if (copyResult == PixelCopy.SUCCESS) {
                persistSnapshot(title, url, bitmap);
              }
            },
            new Handler(Looper.getMainLooper()));
        return;
      } catch (Exception ignored) {
        // Fall through to software draw.
      }
    }

    // Fallback: software draw (may be blank on some hardware-accelerated WebViews)
    try {
      Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
      Canvas canvas = new Canvas(bitmap);
      webView.draw(canvas);
      persistSnapshot(title, url, bitmap);
    } catch (Exception ignored) {
      // Give up gracefully.
    }
  }

  /**
   * Writes the snapshot bitmap to the app's private storage and records the
   * entry in {@link RecentsManager}.
   */
  private void persistSnapshot(String title, String url, Bitmap bitmap) {
    if (getContext() == null || bitmap == null) {
      return;
    }
    File dir = new File(getContext().getFilesDir(), "recents");
    if (!dir.exists() && !dir.mkdirs()) {
      return;
    }
    String id = UUID.randomUUID().toString();
    File file = new File(dir, id + ".png");
    try (FileOutputStream out = new FileOutputStream(file)) {
      bitmap.compress(Bitmap.CompressFormat.PNG, 75, out);
      RecentsManager.getInstance().add(title, url, file.getAbsolutePath());
    } catch (Exception ignored) {
      // Storage unavailable; skip recording this snapshot.
    }
  }

  private void loadUrl(String raw) {
    if (raw == null || raw.isEmpty()) {
      return;
    }
    String target = raw;
    if (!target.startsWith("http://") && !target.startsWith("https://")
        && !target.startsWith("about:") && !target.startsWith("file:")) {
      target = "https://" + target;
    }
    webView.loadUrl(target);
  }

  /**
   * Reports whether the browser can navigate back.
   */
  public boolean canGoBack() {
    return webView != null && webView.canGoBack();
  }

  /**
   * Navigates back one step in browser history.
   */
  public void goBack() {
    if (webView != null && webView.canGoBack()) {
      webView.goBack();
    } else if (getActivity() instanceof MainNavigator) {
      ((MainNavigator) getActivity()).goHome();
    }
  }

  @Override
  public void onDestroyView() {
    if (webView != null) {
      webView.destroy();
      webView = null;
    }
    super.onDestroyView();
  }
}

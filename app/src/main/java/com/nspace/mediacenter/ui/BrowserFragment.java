package com.nspace.mediacenter.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
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
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import com.nspace.mediacenter.R;
import com.nspace.mediacenter.core.HistoryManager;
import com.nspace.mediacenter.core.RecentsManager;
import java.io.File;
import java.io.FileOutputStream;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

  // Desktop browser UA so video portals (e.g. mgtv.com) serve the full web
  // player instead of their mobile layout. The car head unit is a 1920x1080
  // screen, so the desktop rendering fits much better than a phone layout.
  private static final String DESKTOP_USER_AGENT =
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
          + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

  private WebView webView;
  private ProgressBar progressBar;

  // HTML5 video fullscreen support (e.g. the MGTV player's fullscreen button).
  private View mCustomView;
  private WebChromeClient.CustomViewCallback mCustomViewCallback;
  private FrameLayout mFullscreenContainer;

  // Throttle consecutive snapshots of the same URL (e.g. reloads / back-forward)
  private String lastCaptureUrl;
  private long lastCaptureTime;

  // Background thread for snapshot encoding + disk write, so a captured frame
  // never blocks the UI thread (the RK3588 head unit is tight on CPU/RAM).
  private static final ExecutorService SNAPSHOT_EXECUTOR = Executors.newSingleThreadExecutor();

  // Snapshots are only shown as small 16:9 "Continue Playing" cards on the home
  // screen, so capturing the full 1920x1080 surface is pure waste. Cap the
  // longest side to keep the bitmap ~1.1MB instead of ~8.3MB and speed encode.
  private static final int CAPTURE_MAX_SIDE = 720;

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
    // Request the desktop version of web pages: without this the WebView's
    // default mobile UA makes sites like mgtv.com return a phone layout that
    // looks broken on the 1080p car screen.
    webView.getSettings().setUseWideViewPort(true);
    webView.getSettings().setLoadWithOverviewMode(true);
    webView.getSettings().setUserAgentString(DESKTOP_USER_AGENT);
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

      @Override
      public void onShowCustomView(View view, CustomViewCallback callback) {
        // Reject a second request while one is already active.
        if (mCustomView != null) {
          callback.onCustomViewHidden();
          return;
        }
        FragmentActivity act = getActivity();
        if (act == null) {
          callback.onCustomViewHidden();
          return;
        }
        mCustomView = view;
        mCustomViewCallback = callback;

        // Hide the in-app browser UI (sidebar, progress, web view) so the
        // video fills the whole screen.
        View root = getView();
        if (root != null) {
          root.setVisibility(View.GONE);
        }

        mFullscreenContainer = new FrameLayout(requireContext());
        mFullscreenContainer.setBackgroundColor(Color.BLACK);
        mFullscreenContainer.addView(view, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ViewGroup decor = (ViewGroup) act.getWindow().getDecorView();
        decor.addView(mFullscreenContainer, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // Immersive mode: drop the status bar and nav bar while playing.
        decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
      }

      @Override
      public void onHideCustomView() {
        if (mCustomView == null) {
          return;
        }
        FragmentActivity act = getActivity();
        if (act != null) {
          ViewGroup decor = (ViewGroup) act.getWindow().getDecorView();
          decor.setSystemUiVisibility(0);
          if (mFullscreenContainer != null) {
            decor.removeView(mFullscreenContainer);
            mFullscreenContainer = null;
          }
        }
        if (mCustomView != null && mCustomView.getParent() instanceof ViewGroup) {
          ((ViewGroup) mCustomView.getParent()).removeView(mCustomView);
        }
        mCustomView = null;

        View root = getView();
        if (root != null) {
          root.setVisibility(View.VISIBLE);
        }
        if (mCustomViewCallback != null) {
          mCustomViewCallback.onCustomViewHidden();
          mCustomViewCallback = null;
        }
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
  /**
   * Computes the dimensions of a downscaled snapshot, preserving the WebView's
   * aspect ratio while capping the longest side at {@link #CAPTURE_MAX_SIDE}.
   * Capturing the full 1920x1080 surface wastes ~8MB of RAM and CPU for a
   * thumbnail that is only ever shown as a ~16:9 "Continue Playing" card.
   */
  private int[] computeCaptureSize(int w, int h) {
    if (w >= h) {
      return new int[] { CAPTURE_MAX_SIDE, Math.max(1, (int) (h * (float) CAPTURE_MAX_SIDE / w)) };
    }
    return new int[] { Math.max(1, (int) (w * (float) CAPTURE_MAX_SIDE / h)), CAPTURE_MAX_SIDE };
  }

  private void captureSnapshot(String title, String url) {
    if (getContext() == null || webView == null || !isAdded()) {
      return;
    }
    final int w = webView.getWidth();
    final int h = webView.getHeight();
    if (w <= 0 || h <= 0) {
      return;
    }
    final int[] size = computeCaptureSize(w, h);
    final int tw = size[0];
    final int th = size[1];

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      try {
        final Bitmap bitmap = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888);
        final Rect srcRect = new Rect();
        webView.getGlobalVisibleRect(srcRect);
        PixelCopy.request(getActivity().getWindow(), srcRect, bitmap,
            copyResult -> {
              if (copyResult == PixelCopy.SUCCESS) {
                persistSnapshot(title, url, bitmap);
              } else {
                bitmap.recycle();
              }
            },
            new Handler(Looper.getMainLooper()));
        return;
      } catch (Exception ignored) {
        // Fall through to software draw.
      }
    }

    // Fallback: software draw straight into a pre-scaled canvas (may be blank
    // on some hardware-accelerated WebViews).
    try {
      Bitmap bitmap = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888);
      Canvas canvas = new Canvas(bitmap);
      canvas.scale((float) tw / w, (float) th / h);
      webView.draw(canvas);
      persistSnapshot(title, url, bitmap);
    } catch (Exception ignored) {
      // Give up gracefully.
    }
  }

  /**
   * Encodes the snapshot bitmap to JPEG and writes it to the app's private
   * storage on a background thread (encoding even a 720px frame is wasteful on
   * the RK3588's UI thread), then records the entry in {@link RecentsManager}.
   * The bitmap is always recycled on the background thread so its pixel buffer
   * is never leaked. JPEG over PNG roughly halves the file size at this size
   * with no visible quality loss on a small card.
   */
  private void persistSnapshot(String title, String url, Bitmap bitmap) {
    if (bitmap == null) {
      return;
    }
    final Context appCtx = (getContext() == null) ? null : getContext().getApplicationContext();
    if (appCtx == null) {
      bitmap.recycle();
      return;
    }
    final String pageTitle = title;
    final String pageUrl = url;
    SNAPSHOT_EXECUTOR.execute(() -> {
      File dir = new File(appCtx.getFilesDir(), "recents");
      if (!dir.exists() && !dir.mkdirs()) {
        bitmap.recycle();
        return;
      }
      String id = UUID.randomUUID().toString();
      File file = new File(dir, id + ".jpg");
      try (FileOutputStream out = new FileOutputStream(file)) {
        if (bitmap.compress(Bitmap.CompressFormat.JPEG, 72, out)) {
          RecentsManager.getInstance().add(pageTitle, pageUrl, file.getAbsolutePath());
        }
      } catch (Exception ignored) {
        // Storage unavailable; skip recording this snapshot.
      } finally {
        bitmap.recycle();
      }
    });
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
      // Best-effort snapshot before teardown: persist the current page to the
      // recents store so the home-screen "Continue Playing" row is populated
      // even when the user leaves via the Home button. goHome() destroys this
      // fragment immediately, before the delayed onPageFinished capture (which
      // runs 1200ms later via postDelayed) has a chance to execute.
      try {
        final String url = webView.getUrl();
        final String title = webView.getTitle();
        final int w = webView.getWidth();
        final int h = webView.getHeight();
        if (url != null && !url.isEmpty() && w > 0 && h > 0) {
          final long now = System.currentTimeMillis();
          if (!url.equals(lastCaptureUrl) || now - lastCaptureTime >= 5000) {
            lastCaptureUrl = url;
            lastCaptureTime = now;
            // Draw straight into a pre-scaled bitmap so we never allocate the
            // full 1920x1080 (~8MB) buffer just before teardown.
            final int[] size = computeCaptureSize(w, h);
            Bitmap bitmap = Bitmap.createBitmap(size[0], size[1], Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.scale((float) size[0] / w, (float) size[1] / h);
            webView.draw(canvas);
            persistSnapshot(title, url, bitmap);
          }
        }
      } catch (Exception ignored) {
        // Best-effort only; never block teardown.
      }
      webView.destroy();
      webView = null;
    }
    // Tear down any active HTML5 fullscreen view so it doesn't leak into the
    // activity decor after this fragment is destroyed.
    if (mCustomView != null && mFullscreenContainer != null) {
      FragmentActivity act = getActivity();
      if (act != null) {
        try {
          ((ViewGroup) act.getWindow().getDecorView()).removeView(mFullscreenContainer);
        } catch (Exception ignored) {
          // Best-effort cleanup.
        }
      }
      mFullscreenContainer = null;
      mCustomView = null;
      if (mCustomViewCallback != null) {
        mCustomViewCallback.onCustomViewHidden();
        mCustomViewCallback = null;
      }
    }
    super.onDestroyView();
  }
}

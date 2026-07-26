package com.nspace.mediacenter.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.PixelCopy;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
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
import com.nspace.mediacenter.BuildConfig;
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
  // NOTE: mobile UA was tried for Chinese portals (haokan/kuaishou) but the
  // mobile pages are app-download funnels that cannot be operated or played
  // on the car screen — desktop UA is used for ALL sites by decision.
  private static final String DESKTOP_USER_AGENT =
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
          + "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";

  // Android WebView's native <video controls> UI has NO volume slider (unlike
  // desktop Chrome), so sites that rely on the native controls (e.g. distro.tv)
  // leave the user unable to change volume on the car head unit. For those
  // hosts we inject a small floating volume widget that drives the page's
  // HTMLMediaElement.volume / muted directly.
  private static final String TAG = "BrowserFragment";
  private static final String VOLUME_WIDGET_HOST = "distro.tv";
  // Builds a floating volume widget when a <video> exists. This is injected
  // repeatedly (see maybeInjectVolumeWidget) so it survives late-appearing
  // videos and page redirects. It is a no-op once the widget already exists.
  private static final String VOLUME_WIDGET_JS =
      "(function(){"
    + "window.__nspaceInj=(window.__nspaceInj||0)+1;"
    + "if(document.getElementById('nspace_vol_widget'))return;"
    + "var v=document.querySelector('video');"
    + "if(!v)return;"
    + "var s=document.createElement('style');"
    + "s.textContent='#nspace_vol_widget{position:fixed;right:18px;top:50%;transform:translateY(-50%);"
    + "z-index:2147483646;background:rgba(0,0,0,0.6);padding:10px 8px;border-radius:12px;"
    + "display:flex;flex-direction:column;align-items:center;gap:10px;font-family:sans-serif;"
    + "opacity:0;transition:opacity 0.25s;pointer-events:none;}"
    + "#nspace_vol_widget.ns-visible{opacity:1;pointer-events:auto;}"
    + "#nspace_vol_btn{background:none;border:none;color:#fff;font-size:14px;font-weight:700;"
    + "cursor:pointer;white-space:nowrap;}"
    + "#nspace_vol_range{writing-mode:vertical-lr;direction:rtl;width:10px;height:130px;}';"
    + "document.head.appendChild(s);"
    + "var w=document.createElement('div');w.id='nspace_vol_widget';"
    + "var b=document.createElement('button');b.id='nspace_vol_btn';"
    + "b.textContent=(v.muted?'Unmute':'Mute');"
    + "var r=document.createElement('input');r.id='nspace_vol_range';r.type='range';"
    + "r.min='0';r.max='1';r.step='0.05';"
    + "var iv=(v.volume==null||isNaN(v.volume)||v.volume<=0)?0.5:v.volume;"
    + "r.value=String(iv);if(v.volume<=0){v.volume=iv;}"
    + "w.appendChild(b);w.appendChild(r);document.body.appendChild(w);"
    + "var hideTimer=null;"
    + "function showWidget(){w.classList.add('ns-visible');"
    + "if(hideTimer)clearTimeout(hideTimer);"
    + "hideTimer=setTimeout(function(){w.classList.remove('ns-visible');},4000);}"
    + "function onActivity(e){if(e&&e.isTrusted===false)return;showWidget();}"
    + "r.addEventListener('input',function(){v.volume=parseFloat(r.value);"
    + "if(v.volume>0)v.muted=false;showWidget();});"
    + "b.addEventListener('click',function(e){e.stopPropagation();v.muted=!v.muted;"
    + "if(!v.muted&&v.volume<=0){v.volume=parseFloat(r.value)||0.5;}"
    + "b.textContent=(v.muted?'Unmute':'Mute');showWidget();});"
    + "w.addEventListener('pointerdown',function(e){e.stopPropagation();showWidget();});"
    + "['click','touchstart','pointerdown','pointermove'].forEach(function(ev){"
    + "document.addEventListener(ev,onActivity,true);});"
    + "})();";

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

  // Periodic volume-widget check. Re-runs the idempotent injection every 3s
  // while the BrowserFragment is alive so it covers retained/persistent
  // WebView renderers (which never fire onPageFinished on a re-attach) as
  // well as normal navigations. The JS itself is a no-op once the widget
  // exists, so the cost is one getUrl() + one JS frame per tick.
  private final Handler volumeHandler = new Handler(Looper.getMainLooper());
  private final Runnable volumeCheck = new Runnable() {
    @Override
    public void run() {
      String u = (webView == null) ? null : webView.getUrl();
      Log.d(TAG, "volumeCheck tick: webView=" + (webView != null) + " url=" + u);
      if (webView != null && u != null && u.contains(VOLUME_WIDGET_HOST)) {
        Log.d(TAG, "volumeCheck: injecting for " + u);
        webView.evaluateJavascript(VOLUME_WIDGET_JS, null);
      }
      volumeHandler.postDelayed(this, 3000);
    }
  };

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

    // Allow Chrome DevTools (chrome://inspect) to attach to this WebView so the
    // in-page player DOM can be inspected/debugged on a real device. Debug-only
    // so a release build never exposes the WebView over CDP.
    if (BuildConfig.DEBUG) {
      WebView.setWebContentsDebuggingEnabled(true);
    }

    webView = view.findViewById(R.id.webview);
    progressBar = view.findViewById(R.id.progress_bar);

    // Start the periodic volume-widget check (see volumeCheck). The first tick
    // runs after 3s so it doesn't race the page-load progress bar.
    volumeHandler.removeCallbacks(volumeCheck);
    volumeHandler.postDelayed(volumeCheck, 3000);

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
    webView.getSettings().setDatabaseEnabled(true);
    webView.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
      webView.getSettings().setMediaPlaybackRequiresUserGesture(false);
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      webView.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
    }
    // Request the desktop version of web pages: without this the WebView's
    // default mobile UA makes sites like mgtv.com return a phone layout that
    // looks broken on the 1080p car screen.
    webView.getSettings().setUseWideViewPort(true);
    webView.getSettings().setLoadWithOverviewMode(true);
    webView.getSettings().setUserAgentString(DESKTOP_USER_AGENT);
    webView.setWebViewClient(new WebViewClient() {
      @Override
      public void onPageStarted(WebView view, String url, Bitmap favicon) {
        super.onPageStarted(view, url, favicon);
      }

      @Override
      public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        String url = request == null ? null : request.getUrl().toString();
        if (url != null && !url.startsWith("http://") && !url.startsWith("https://")
            && !url.startsWith("about:") && !url.startsWith("javascript:")) {
          return true;
        }
        return super.shouldOverrideUrlLoading(view, request);
      }

      @Override
      public boolean shouldOverrideUrlLoading(WebView view, String url) {
        // Block custom app schemes (e.g. baiduhaokan://) that the WebView cannot
        // render. Letting them through shows ERR_UNKNOWN_URL_SCHEME.
        if (url != null && !url.startsWith("http://") && !url.startsWith("https://")
            && !url.startsWith("about:") && !url.startsWith("javascript:")) {
          return true;
        }
        return super.shouldOverrideUrlLoading(view, url);
      }

      @Override
      public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);
        String title = view.getTitle();
        HistoryManager.getInstance().addVisit(title == null ? url : title, url);
        // Update forward button state
        btnForward.setAlpha(view.canGoForward() ? 1f : 0.3f);
        // Capture a snapshot so the home screen can offer "Continue Playing"
        scheduleCapture(title == null ? url : title, url);
        // Sites whose player relies on native <video controls> get no volume
        // slider on WebView — inject our own floating volume widget.
        maybeInjectVolumeWidget(url);
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
   * Injects the floating volume widget for hosts whose player relies on the
   * native <video controls> (which lack a volume slider on Android WebView).
   * The widget drives the page's HTMLMediaElement.volume / muted directly.
   *
   * <p>The injection is repeated every ~2s for ~40s because the live <video>
   * element often appears several seconds after onPageFinished (slow car-unit
   * network) and the page may redirect (e.g. daystar-tv -> daystar-espanol),
   * which tears down any previously injected widget. A session token cancels
   * an in-flight chain as soon as the user navigates away from the host, so
   * the widget never leaks onto unrelated sites.
   */
  private int volumeWidgetSession = 0;

  private void maybeInjectVolumeWidget(String url) {
    if (url == null || url.isEmpty() || webView == null) {
      Log.d(TAG, "maybeInjectVolumeWidget: skip (url/webView null)");
      return;
    }
    if (!url.contains(VOLUME_WIDGET_HOST)) {
      // Not a volume-widget host (or navigating away): cancel any chain.
      volumeWidgetSession++;
      Log.d(TAG, "maybeInjectVolumeWidget: host not matched, cancel chain: " + url);
      return;
    }
    final int mySession = ++volumeWidgetSession;
    final int[] attempts = {0};
    final int MAX_ATTEMPTS = 20; // ~40s of retries
    Log.d(TAG, "maybeInjectVolumeWidget: start repeating inject (session " + mySession + ") for " + url);
    final Runnable inject = new Runnable() {
      @Override
      public void run() {
        // Snapshot the field: onDestroyView() nulls webView while queued
        // runnables may still be dispatched by the main looper afterwards.
        final WebView wv = webView;
        if (wv == null || mySession != volumeWidgetSession) {
          return; // view destroyed or superseded by a newer navigation
        }
        if (attempts[0]++ >= MAX_ATTEMPTS) {
          return;
        }
        wv.evaluateJavascript(VOLUME_WIDGET_JS, null);
        if (attempts[0] < MAX_ATTEMPTS) {
          wv.postDelayed(this, 2000);
        }
      }
    };
    webView.postDelayed(inject, 0);
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
    volumeHandler.removeCallbacks(volumeCheck);
    // Invalidate any in-flight inject retry chain (see maybeInjectVolumeWidget):
    // its runnables are posted on the WebView/main looper and may still fire
    // after this fragment's view is torn down.
    volumeWidgetSession++;
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

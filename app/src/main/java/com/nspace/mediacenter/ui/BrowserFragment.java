package com.nspace.mediacenter.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
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
import android.webkit.WebResourceError;
import android.webkit.WebResourceResponse;
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
import com.nspace.mediacenter.media.MediaWebViewHolder;
import com.nspace.mediacenter.media.PlaybackService;
import com.nspace.mediacenter.util.AdBlocker;
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

  // Visual-layer ad hiding. Complements the network blocker in
  // shouldInterceptRequest. Only display:none on nodes that look like ads
  // (ad/banner class or id, or data-ad markers) — never removes real
  // functionality, so pages keep working normally.
  private static final String AD_HIDE_JS =
      "(function(){try{"
    + "var css='[class*=\"ad-\"],[class*=\"ads\"],[class*=\"adv\"],[class*=\"advert\"],"
    + "[id*=\"ad-\"],[id*=\"ads\"],[class*=\"banner\"],[id*=\"banner\"],"
    + "[data-ad],[data-ads],[data-ad-client],[data-ad-slot]{display:none!important;}';"
    + "var st=document.createElement('style');st.type='text/css';"
    + "st.appendChild(document.createTextNode(css));"
    + "(document.head||document.documentElement).appendChild(st);"
    + "function hide(n){if(!n||n.nodeType!==1)return;"
    + "if(n.matches&&n.matches('[class*=\"ad-\"],[class*=\"ads\"],[class*=\"adv\"],"
    + "[class*=\"advert\"],[id*=\"ad-\"],[id*=\"ads\"],[class*=\"banner\"],[id*=\"banner\"],"
    + "[data-ad],[data-ads]'))n.style.display='none';"
    + "var els=n.querySelectorAll('[class*=\"ad-\"],[class*=\"ads\"],[class*=\"adv\"],"
    + "[class*=\"advert\"],[id*=\"ad-\"],[id*=\"ads\"],[class*=\"banner\"],[id*=\"banner\"],"
    + "[data-ad],[data-ads]');"
    + "for(var i=0;i<els.length;i++)els[i].style.display='none';}"
    + "hide(document);"
    + "var t;var obs=new MutationObserver(function(m){if(t)return;"
    + "t=setTimeout(function(){t=null;for(var k=0;k<m.length;k++){var r=m[k];"
    + "for(var i=0;i<r.addedNodes.length;i++)hide(r.addedNodes[i]);}},300);});"
    + "obs.observe(document.documentElement,{childList:true,subtree:true});"
    + "}catch(e){}})();";

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

  // Haokan's own HTML5 player paints a "播放出现小问题" (playback hit a small
  // problem) toast during the first second of buffering on the slow car unit;
  // it self-dismisses once the stream starts. We pre-emptively hide that toast
  // (scoped to the haokan host) so the user only ever sees smooth playback.
  private static final String HAOKAN_HIDE_ERROR_JS =
      "(function(){"
    + "if(window.__nspaceHaokanHide)return;"
    + "window.__nspaceHaokanHide=1;"
    + "function hideErr(){"
    + "var all=document.querySelectorAll('*');"
    + "for(var i=0;i<all.length;i++){"
    + "var t=all[i].textContent||'';"
    + "if(t.indexOf('播放出现小问题')>=0){"
    + "var el=all[i];"
    + "while(el&&el!==document.documentElement){"
    + "var pos=window.getComputedStyle(el).position;"
    + "if(pos==='fixed'||pos==='absolute')break;"
    + "el=el.parentNode;"
    + "}"
    + "if(el&&el.style)el.style.display='none';"
    + "}"
    + "}"
    + "}"
    + "var obs=new MutationObserver(function(){hideErr();});"
    + "obs.observe(document.documentElement,{childList:true,subtree:true});"
    + "setTimeout(hideErr,1500);setTimeout(hideErr,4000);setTimeout(hideErr,8000);"
    + "})();";

  private void maybeHideHaokanError() {
    if (webView == null) {
      return;
    }
    webView.evaluateJavascript(HAOKAN_HIDE_ERROR_JS, null);
  }

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
      // Stale-reference guard: if the shared holder no longer owns our WebView
      // it was released (destroyed) externally -- e.g. PlaybackService reclaimed
      // it while the app was backgrounded. Drop the dead reference and stop
      // ticking instead of spamming "destroyed WebView" warnings every 3s.
      if (webView != null && MediaWebViewHolder.getInstance().get() != webView) {
        Log.w(TAG, "volumeCheck: WebView was released externally; stopping ticks");
        webView = null;
        return;
      }
      String u = null;
      try {
        u = (webView == null) ? null : webView.getUrl();
      } catch (Exception ignored) {
        // Destroyed WebView race; treated as no URL.
      }
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

    sAlive = true;
    progressBar = view.findViewById(R.id.progress_bar);

    // Resolve the WebView from the shared holder (reused across navigations so
    // audio keeps playing when we leave this fragment) or create it on first
    // use. Starts the foreground MediaSession service that drives lock-screen /
    // notification playback controls.
    webView = resolveWebView(view);
    ensurePlaybackService();

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
      // ALWAYS_ALLOW: Haokan/Kuaishou players pull video segments and player
      // assets over mixed schemes; the COMPATIBILITY_MODE default can
      // intermittently block some of them, which surfaces as a transient
      // "播放出现了小问题" before the stream recovers. Permitting all mixed
      // content keeps the player loading smoothly.
      webView.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
    }
    // Request the desktop version of web pages: without this the WebView's
    // default mobile UA makes sites like mgtv.com return a phone layout that
    // looks broken on the 1080p car screen.
    webView.getSettings().setUseWideViewPort(true);
    webView.getSettings().setLoadWithOverviewMode(true);
    webView.getSettings().setUserAgentString(DESKTOP_USER_AGENT);
    webView.setWebViewClient(new WebViewClient() {
      @Override
      public WebResourceResponse shouldInterceptRequest(WebView view,
          WebResourceRequest request) {
        // Only ever block SUB-RESOURCES (images/scripts/css/iframes) hosted on
        // known third-party ad networks. The main document is never touched, so
        // navigation and page functionality are always preserved. Blocked ads
        // get an empty 200 response — the page doesn't even notice they're gone.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
            && !request.isForMainFrame()) {
          final String url = request.getUrl().toString();
          if (AdBlocker.isAd(url)) {
            if (BuildConfig.DEBUG) {
              Log.d(TAG, "ad blocked: " + url);
            }
            return AdBlocker.emptyResponse();
          }
        }
        return null;
      }

      @Override
      public void onPageStarted(WebView view, String url, Bitmap favicon) {
        super.onPageStarted(view, url, favicon);
        // Reveal the WebView now that the new page has begun loading. The stale
        // frame from the previous app was hidden in resolveWebView and cleared
        // by the Surface rebuild, so showing it now only ever reveals the new
        // content (never the leftover previous-app picture).
        view.setVisibility(View.VISIBLE);
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
      public void onReceivedError(WebView view, WebResourceRequest request,
          WebResourceError error) {
        super.onReceivedError(view, request, error);
        // Reveal the WebView ONLY on main-frame errors. Blocked ads are
        // sub-resources that fail on purpose — those must not flip visibility
        // or spam the log here.
        if (request == null || request.isForMainFrame()) {
          view.setVisibility(View.VISIBLE);
        }
      }

      @Override
      public void onReceivedHttpError(WebView view, WebResourceRequest request,
          WebResourceResponse errorResponse) {
        super.onReceivedHttpError(view, request, errorResponse);
        view.setVisibility(View.VISIBLE);
      }

      @Override
      public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);
        // Defensive: always ensure the WebView is visible once a page finishes.
        view.setVisibility(View.VISIBLE);
        String title = view.getTitle();
        HistoryManager.getInstance().addVisit(title == null ? url : title, url);
        // Update forward button state
        btnForward.setAlpha(view.canGoForward() ? 1f : 0.3f);
        // Capture a snapshot so the home screen can offer "Continue Playing"
        scheduleCapture(title == null ? url : title, url);
        // Sites whose player relies on native <video controls> get no volume
        // slider on WebView — inject our own floating volume widget.
        maybeInjectVolumeWidget(url);
        // Haokan's player shows a transient "播放出现了小问题" toast while
        // buffering; hide it so only smooth playback is visible.
        if (url != null && url.contains("haokan")) {
          maybeHideHaokanError();
        }
        // Expose a media-element locator so the background MediaSession poller
        // can read play/pause progress and drive lock-screen / notification
        // playback controls. Idempotent.
        if (webView != null) {
          webView.evaluateJavascript(MediaWebViewHolder.FIND_MEDIA_JS, null);
          // Hide banner / popup ad containers (visual layer, complements the
          // network blocking in shouldInterceptRequest). Non-destructive: only
          // display:none on ad-likely nodes, never breaks page functionality.
          webView.evaluateJavascript(AD_HIDE_JS, null);
        }
        ensurePlaybackService();
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
   * Returns the shared WebView: creates it on first use, or re-attaches the
   * retained instance (owned by {@link MediaWebViewHolder}) on later openings.
   * When reusing, the freshly inflated layout WebView is discarded so exactly
   * one WebView ever exists and audio survives fragment replacement.
   *
   * <p>When a retained WebView is reused, it is re-parented from its previous
   * container (often {@code R.id.webview_host}, where it was parked to keep
   * background audio alive) into this fragment's frame. A WebView renders to a
   * dedicated Surface; moving it between containers can leave that Surface
   * "frozen" on the previous app's last frame even after we navigate to the new
   * URL — so the screen keeps showing the old app until the new page paints.
   * This is exactly the "switch to another app and the previous app's picture
   * stays on screen" bug. To clear the frozen frame we detach and re-attach the
   * WebView on the next looper pass, which forces Chromium to recreate its
   * Surface and repaint the current (new) page.
   */
  private WebView resolveWebView(@NonNull View view) {
    FrameLayout frame = view.findViewById(R.id.browser_web_frame);
    WebView held = MediaWebViewHolder.getInstance().get();
    if (held != null) {
      WebView fresh = view.findViewById(R.id.webview);
      if (fresh != null && fresh != held) {
        if (fresh.getParent() instanceof ViewGroup) {
          ((ViewGroup) fresh.getParent()).removeView(fresh);
        }
        fresh.destroy();
      }
      if (held.getParent() instanceof ViewGroup) {
        ((ViewGroup) held.getParent()).removeView(held);
      }
      frame.addView(held, new FrameLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      // Hide the reused WebView immediately so the previous app's last frame
      // (frozen on the Surface from the prior container) can never be shown
      // during the re-attach + new-page load. It is made visible again in
      // WebViewClient.onPageStarted / onPageFinished once the new content
      // begins painting. See the class javadoc on the residue bug.
      held.setVisibility(View.INVISIBLE);
      // Force the WebView's Surface to be recreated so any frozen frame from
      // the previous container (and thus the previous app) is dropped and the
      // newly navigated page is shown. See method javadoc. Kept light (no
      // bringToFront/requestLayout) to avoid an extra layout storm that can
      // stall the main-window BufferQueue on the slow RK3588 head unit.
      held.post(() -> {
        try {
          if (held.getParent() instanceof ViewGroup) {
            ViewGroup p = (ViewGroup) held.getParent();
            p.removeView(held);
            p.addView(held, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
          }
          // Stay hidden until the new page paints (onPageStarted).
          held.setVisibility(View.INVISIBLE);
        } catch (Exception ignored) {
          // Best-effort; the navigation to the new URL still proceeds.
        }
      });
      // Last-resort safety net: if no page callback ever fires (e.g. a
      // navigation that never commits), reveal the WebView after a short delay
      // so it is never left permanently invisible. By then the Surface rebuild
      // above has cleared the stale frame, so this only ever shows new content.
      held.postDelayed(() -> held.setVisibility(View.VISIBLE), 4000);
      return held;
    }
    WebView wv = view.findViewById(R.id.webview);
    MediaWebViewHolder.getInstance().set(wv);
    return wv;
  }

  /**
   * Starts the foreground MediaSession service (normal start; the service
   * promotes itself to foreground once it detects an active media element).
   * Safe to call repeatedly while the fragment is attached.
   */
  private void ensurePlaybackService() {
    try {
      requireContext().startService(new Intent(requireContext(), PlaybackService.class));
    } catch (Exception ignore) {
      // Service start is best-effort; media controls degrade gracefully.
    }
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

  /**
   * Whether a BrowserFragment is currently the visible (foreground) fragment.
   * {@link com.nspace.mediacenter.media.PlaybackService} reads this to decide
   * whether it is safe to release the retained WebView when playback stops:
   * if the user is still looking at the browser page we keep it (they may
   * resume), but if they have navigated away we free it to reclaim memory.
   *
   * <p>Named {@code isBrowserVisible} (not {@code isVisible}) because
   * {@code Fragment} already declares a non-static {@code isVisible()}; a static
   * method with the same signature cannot coexist with it and fails to compile.
   */
  private static volatile boolean sVisible = false;

  public static boolean isBrowserVisible() {
    return sVisible;
  }

  /**
   * Whether a BrowserFragment instance currently exists (its view is created
   * and not yet destroyed). Unlike {@link #isBrowserVisible()}, this stays true
   * while the whole app is backgrounded (fragment paused but alive).
   *
   * <p>{@link com.nspace.mediacenter.media.PlaybackService} must NOT release
   * (destroy) the shared WebView while this is true: the live fragment still
   * holds a reference to it, and destroying it underneath the fragment leaves a
   * dead page plus a "destroyed WebView" warning storm from the volumeCheck
   * poller when the user returns to the app.
   */
  private static volatile boolean sAlive = false;

  public static boolean isBrowserAlive() {
    return sAlive;
  }

  @Override
  public void onResume() {
    super.onResume();
    sVisible = true;
    if (webView != null) {
      // Resume WebView rendering and JS timers when this fragment is visible
      // again. resumeTimers() is process-global but we only ever host a single
      // WebView, so pairing it with pauseTimers() in onPause() is safe.
      webView.onResume();
      webView.resumeTimers();
    }
  }

  @Override
  public void onPause() {
    sVisible = false;
    // Deliberately do NOT pause the WebView here. Calling onPause() /
    // pauseTimers() also halts HTMLMediaElement playback, which is exactly the
    // "music stops when I switch to the home screen" bug. The WebView is
    // retained (see onDestroyView) and a foreground MediaSession service keeps
    // the process alive, so audio keeps playing. Rendering naturally stops
    // because the view is detached from the window while we're in the background.
    super.onPause();
  }

  @Override
  public void onDestroyView() {
    sAlive = false;
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
      // Decide whether to keep the WebView alive or release it. We only retain
      // it when audio is ACTIVELY playing: that is the only case where the user
      // benefits from background playback. If nothing is playing, we destroy the
      // WebView (and its ~280MB renderer process) immediately, reverting to the
      // pre-v1.0.18 behaviour so memory is reclaimed after every browse -- this
      // is what stops the head unit from feeling sluggish over time.
      if (PlaybackService.isPlaying()) {
        // Keep alive: re-parent into the always-on window host
        // (R.id.webview_host) rather than just removing it from the window. A
        // WebView that loses its window makes Chromium mark the page "hidden",
        // which pauses <video>/<audio> playback -- that was the original "music
        // stops when I go home" bug. Re-parenting keeps it attached to a live,
        // visible window (merely covered by content_frame), so media continues.
        // The next BrowserFragment re-attaches it into browser_web_frame and
        // re-applies the clients.
        try {
          webView.setWebChromeClient(null);
          webView.setWebViewClient(null);
          final ViewGroup host =
              (ViewGroup) requireActivity().findViewById(R.id.webview_host);
          if (host != null) {
            if (webView.getParent() instanceof ViewGroup) {
              ((ViewGroup) webView.getParent()).removeView(webView);
            }
            host.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
          } else if (webView.getParent() instanceof ViewGroup) {
            ((ViewGroup) webView.getParent()).removeView(webView);
          }
        } catch (Exception ignored) {
          // Best-effort cleanup.
        }
      } else {
        // Nothing playing -> free everything now.
        MediaWebViewHolder.getInstance().release();
        PlaybackService.stopIfInactive();
      }
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

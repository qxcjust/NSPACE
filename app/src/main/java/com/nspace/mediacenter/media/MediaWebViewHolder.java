package com.nspace.mediacenter.media;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.WebView;
import androidx.annotation.Nullable;

/**
 * Process-wide owner of the single shared {@link WebView} used for media
 * playback.
 *
 * <p>Why this exists: NSpace swaps fragments with {@code replace()}, so the
 * {@code BrowserFragment} is destroyed (and its WebView with it) every time the
 * user returns to the home screen. For a car media app we want audio to keep
 * playing after that navigation, so the WebView is parked here and detached /
 * re-attached across fragment recreations instead of being destroyed.
 *
 * <p>All WebView touches are posted to the main looper because the WebView was
 * created on the UI thread and {@code evaluateJavascript} must run there.
 */
public final class MediaWebViewHolder {

  private static final String TAG = "MediaWebViewHolder";

  private static final MediaWebViewHolder INSTANCE = new MediaWebViewHolder();

  public static MediaWebViewHolder getInstance() {
    return INSTANCE;
  }

  /**
   * Injected once per page so the background MediaSession poller can locate the
   * active media element regardless of which site is loaded. Runs in page
   * context (same-origin), so it can read the page's own audio/video element.
   */
  public static final String FIND_MEDIA_JS =
      "window.__nsFindMedia=function(){"
          + "var v=document.querySelector('video'); if(v)return v;"
          + "var a=document.querySelector('audio'); if(a)return a;"
          + "var all=document.querySelectorAll('video,audio');"
          + "for(var i=0;i<all.length;i++){if(!all[i].paused)return all[i];}"
          + "return all.length?all[0]:null;};";

  private static final String POLL_JS =
      "(function(){try{"
          + "var m=(typeof window.__nsFindMedia==='function')?window.__nsFindMedia():null;"
          // Return a plain object (not JSON.stringify). evaluateJavascript encodes
          // the JS result as JSON automatically; if we return a string it double-
          // encodes it and JSONObject fails to parse.
          + "if(!m)return {paused:true,current:0,duration:0,title:document.title};"
          + "return {paused:m.paused,"
          + "current:Math.round((m.currentTime||0)*1000),"
          + "duration:Math.round((m.duration||0)*1000),"
          + "title:(m.title||document.title||'')};"
          + "}catch(e){return {paused:true,current:0,duration:0,title:document.title};}})();";

  /** Callback delivering the JSON produced by {@link #POLL_JS}. */
  public interface StateCallback {
    void onState(@Nullable String json);
  }

  private WebView webView;

  public void set(@Nullable WebView wv) {
    this.webView = wv;
  }

  @Nullable
  public WebView get() {
    return webView;
  }

  /**
   * Fully tear down the retained WebView. Used when the user stops playback
   * from the MediaSession (lock screen / notification), so the audio process
   * and its memory are released.
   */
  public void release() {
    final WebView wv = webView;
    webView = null;
    if (wv == null) {
      return;
    }
    new Handler(Looper.getMainLooper()).post(() -> {
      try {
        wv.stopLoading();
      } catch (Exception ignore) {
        // best-effort
      }
      try {
        wv.loadUrl("about:blank");
      } catch (Exception ignore) {
        // best-effort
      }
      try {
        wv.destroy();
      } catch (Exception ignore) {
        // best-effort
      }
    });
  }

  private void eval(final String js) {
    final WebView wv = webView;
    if (wv == null) {
      return;
    }
    new Handler(Looper.getMainLooper()).post(() -> {
      try {
        wv.evaluateJavascript(js, null);
      } catch (Exception ignore) {
        // WebView detached or gone; nothing to do.
      }
    });
  }

  public void play() {
    eval("(function(){var m=(typeof window.__nsFindMedia==='function')?window.__nsFindMedia():null;"
        + "if(m)m.play();})();");
  }

  public void pause() {
    eval("(function(){var m=(typeof window.__nsFindMedia==='function')?window.__nsFindMedia():null;"
        + "if(m)m.pause();})();");
  }

  public void seekTo(long posMs) {
    eval("(function(){var m=(typeof window.__nsFindMedia==='function')?window.__nsFindMedia():null;"
        + "if(m)m.currentTime=" + (posMs / 1000.0) + ";})();");
  }

  /**
   * Set the media element's volume (0.0–1.0). Used by PlaybackService to duck
   * audio when another app (e.g. navigation voice guidance) grabs transient
   * audio focus with {@code AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK}, and to restore
   * it when focus is regained.
   */
  public void setVolume(float vol) {
    eval("(function(){var m=(typeof window.__nsFindMedia==='function')?window.__nsFindMedia():null;"
        + "if(m)m.volume=" + vol + ";})();");
  }

  /**
   * Reads the current media element state and reports it as JSON via {@code cb}.
   * Runs on the main thread so the result callback is safe to use for UI /
   * MediaSession updates.
   */
  public void pollState(final StateCallback cb) {
    final WebView wv = webView;
    if (wv == null || cb == null) {
      return;
    }
    new Handler(Looper.getMainLooper()).post(() -> {
      try {
        wv.evaluateJavascript(POLL_JS, value -> {
          if (value != null) {
            cb.onState(value);
          }
        });
      } catch (Exception ignore) {
        // WebView detached; ignore.
      }
    });
  }
}

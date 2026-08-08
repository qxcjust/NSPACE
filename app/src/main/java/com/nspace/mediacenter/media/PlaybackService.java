package com.nspace.mediacenter.media;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import androidx.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.List;
import com.nspace.mediacenter.BuildConfig;
import com.nspace.mediacenter.R;
import com.nspace.mediacenter.ui.BrowserFragment;
import com.nspace.mediacenter.ui.MainActivity;
import org.json.JSONObject;

/**
 * Foreground service that hosts a framework {@link MediaSession} so NSpace's
 * in-WebView audio keeps playing after the user navigates to the home screen
 * or another app, and exposes lock-screen / notification / headset controls.
 *
 * <p>The actual audio is produced by a {@link android.webkit.WebView} owned by
 * {@link MediaWebViewHolder}; this service only bridges system media commands
 * (play / pause / seek / stop) to that WebView via injected JavaScript and
 * publishes playback state back to the system.
 *
 * <p>Uses the framework MediaSession (android.media.session) rather than
 * AndroidX Media3 so no extra Gradle dependency is required — important because
 * the build environment is offline and Media3 is not in the local cache.
 */
public final class PlaybackService extends android.app.Service {

  private static final String TAG = "PlaybackService";
  private static final String CHANNEL_ID = "nspace_media";
  private static final int NOTIF_ID = 1001;

  private MediaSession session;
  private final Handler pollHandler = new Handler(Looper.getMainLooper());
  private Runnable pollRunnable;
  private boolean foreground = false;

  // ── Audio focus resume ──────────────────────────────────────────
  // Chromium's internal AudioFocusDelegate pauses our media when another app
  // (music player, phone call, navigation) grabs audio focus. For a permanent
  // LOSS (another app finishes / phone call ends) Chromium ABANDONS focus and
  // keeps its duck volume multiplier at 0 — so when we call play() again the
  // media is "playing" (time advances, subtitles scroll) but SILENT.
  //
  // We fix this with a "focus kick": after an external interruption we resume
  // our media AND request audio focus ourselves (request + hold). Chromium's
  // AudioFocusDelegate then receives AUDIOFOCUS_GAIN and resets its multiplier,
  // restoring sound. We must NOT react to LOSS in our own listener — doing so
  // re-triggered the v2.1.0.41/2.1.0.42 ping-pong (our request vs Chromium's
  // own, same UID, two competing focus clients). We only request focus when
  // resuming after an interruption, never on a fresh user play (Chromium's own
  // request handles that), which is what avoids the conflict.
  private AudioManager audioManager;
  private boolean wasExternalActive = false;
  private long lastPlayingTime = 0;
  private boolean interruptedByExternal = false;
  private int kickRetries = 0;            // auto-resume attempts remaining (budget)
  private long lastResumeAttempt = 0;     // throttle auto-resume attempts
  private boolean focusHeld = false;
  private boolean kickSucceeded = false;  // last kickAudioFocus() was granted
  private AudioFocusRequest focusRequest;

  // Self-managed "we are playing" flag. The in-WebView JS poll (sPlaying) is
  // UNRELIABLE while the app is backgrounded (the renderer is throttled / its
  // evaluateJavascript stalls), which previously caused a self-oscillation:
  // after we resumed and kicked focus, the stale sPlaying=false made
  // updateFromJson() abandon the focus we just acquired → audio dropped →
  // isMusicActive() went false → resume fired again → abandon again … the
  // play/pause/play flapping the user saw in the background. We therefore track
  // our own playing state explicitly instead of trusting the JS poll in bg.
  private boolean wePlaying = false;
  private int myUid = -1;
  // Reflection handles for AudioPlaybackConfiguration. The offline compileSdk 34
  // stubs lack these symbols, so we resolve them at runtime to detect — by UID —
  // whether ANOTHER app is actively playing audio. This is the reliable signal
  // that replaces isMusicActive(): it distinguishes "we are playing" from
  // "an external app is playing" even when the WebView is backgrounded.
  private static Method sGetActivePlaybackConfigs;
  private static Method sCfgIsActive;
  private static Method sCfgGetClientUid;

  // ── Kick judgment-window tuning (v2.1.0.46 → 2.1.0.49) ─────
  // An interruption is latched on the RISING EDGE of external audio (another
  // app grabbing focus) with NO time gate. The old 5s lastPlayingTime gate was
  // deadly for cross-origin iframe players (Stingray / Youtube Music / …):
  // our injected JS cannot see the iframe's <video>, so sPlaying / lastPlayingTime
  // never update and the gate silently disabled recovery for those players.
  // Delay the focus kick after a detected stop so it lands after the external
  // app has fully released audio focus; an early kick can be denied (or the
  // external app re-grabs), leaving Chromium's duck multiplier at 0 = silent.
  private static final long KICK_DELAY_MS = 400;
  // Throttle auto-resume retries while an interruption is latched and we are
  // not actually playing. Lets the forwarded media key (see MediaWebViewHolder
  // .dispatchMediaKey) reach Chromium once the user returns to the foreground
  // and the WebView is refocused — needed for cross-origin iframe players
  // (Stingray / Deezer / …) whose element our injected JS cannot reach.
  private static final long RESUME_RETRY_MS = 4000;
  // Total auto-resume attempts before we give up and let the user resume
  // manually. Generous so a user who comes back within a couple of minutes
  // still gets auto-recovered.
  private static final int RESUME_MAX_ATTEMPTS = 30;

  // Recovery "kick" listener: a deliberate no-op. We request audio focus on
  // resume (see kickAudioFocus) so Chromium — which abandons focus when another
  // app takes over — receives AUDIOFOCUS_GAIN and resets its duck volume
  // multiplier (otherwise stuck at 0, leaving our media "playing" but silent).
  // We must NOT react to LOSS here: that re-triggered the v2.1.0.41/2.1.0.42
  // ping-pong conflict (our request vs Chromium's own, same UID).
  private final AudioManager.OnAudioFocusChangeListener kickListener =
      new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int focusChange) {
          // No-op by design.
        }
      };

  /** True while the page's media element is actually playing (not paused). */
  private static volatile boolean sPlaying = false;
  private static PlaybackService sInstance = null;

  /** Whether audio is currently playing. Read by BrowserFragment to decide
   *  whether the retained WebView must stay alive when navigating away. */
  public static boolean isPlaying() {
    return sPlaying;
  }

  /** Releases the retained WebView and stops the service, but only when nothing
   *  is playing. Called by BrowserFragment when it tears down without active
   *  playback, so the ~280MB Chromium renderer is reclaimed instead of lingering. */
  public static void stopIfInactive() {
    if (sInstance != null && !sPlaying) {
      MediaWebViewHolder.getInstance().release();
      sInstance.stopSelf();
    }
  }

  @Override
  public void onCreate() {
    super.onCreate();

    audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
    myUid = getApplicationInfo().uid;

    session = new MediaSession(this, "NSpaceMedia");
    session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS
        | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
    session.setCallback(new MediaSession.Callback() {
      private final Handler h = new Handler(Looper.getMainLooper());

      @Override
      public void onPlay() {
        wePlaying = true;
        h.post(() -> {
          MediaWebViewHolder.getInstance().play();
          // Also forward a media key so Chromium controls cross-origin iframe
          // players (Stingray / Deezer / …) that our injected JS cannot reach.
          MediaWebViewHolder.getInstance().dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY);
        });
      }

      @Override
      public void onPause() {
        interruptedByExternal = false;
        kickRetries = 0;
        wePlaying = false;
        abandonFocus();
        h.post(() -> {
          MediaWebViewHolder.getInstance().pause();
          MediaWebViewHolder.getInstance().dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE);
        });
      }

      @Override
      public void onSeekTo(long pos) {
        h.post(() -> MediaWebViewHolder.getInstance().seekTo(pos));
      }

      @Override
      public void onStop() {
        wePlaying = false;
        MediaWebViewHolder.getInstance().release();
        stopSelf();
      }
    });
    session.setActive(true);

    pollRunnable = new Runnable() {
      @Override
      public void run() {
        MediaWebViewHolder.getInstance().pollState(json -> updateFromJson(json));
        checkExternalPlayback();
        pollHandler.postDelayed(this, 1000);
      }
    };
    pollHandler.postDelayed(pollRunnable, 1000);

    sInstance = this;
    Log.d(TAG, "PlaybackService created");
  }

  private void updateFromJson(@Nullable String json) {
    boolean paused = true;
    long current = 0;
    long duration = 0;
    String title = "NSpace";
    if (json != null) {
      try {
        JSONObject o = new JSONObject(json);
        paused = o.optBoolean("paused", true);
        current = o.optLong("current", 0);
        duration = o.optLong("duration", 0);
        String t = o.optString("title", "");
        if (t != null && !t.isEmpty()) {
          title = t;
        }
      } catch (Exception e) {
        Log.w(TAG, "bad state json: " + json, e);
      }
    }

    if (!paused) {
      lastPlayingTime = System.currentTimeMillis();
    }
    sPlaying = !paused;
    // Track our own playing state from the page poll so the external-audio
    // detection fallback and the focus-abandon guard have an accurate signal.
    // (The MediaSession callbacks also set it, but a plain in-page play does not
    // go through them, so without this wePlaying would stay false during normal
    // playback and the fallback would mis-detect "external audio".)
    wePlaying = sPlaying;

    // On fresh user play Chromium manages its own focus, so we leave it alone.
    // When playback pauses / ends (including an external-app interruption that
    // paused us), release our held focus so we don't block the system stream.
    // BUT while an external interruption is latched we KEEP the focus we kicked
    // for so the recovery kick can actually reset Chromium's duck multiplier
    // (for cross-origin iframe players sPlaying is always false, so without
    // this guard the kick would be abandoned the very next poll).
    // Release our held focus only when WE are not playing (wePlaying). We must
    // NOT abandon while we believe we are playing — that previously (with the
    // background-stale sPlaying) dropped the focus we just kicked and caused the
    // play/pause flapping. wePlaying is self-managed and reliable in background.
    // Release our held focus only when WE are not playing AND we are in the
    // FOREGROUND. In the background the WebView's JS poll is throttled / stalls,
    // so sPlaying (and thus wePlaying) can go stale-false and would otherwise
    // make us abandon the focus we just kicked for recovery — that was the
    // play/pause/play flapping the user saw after closing Douyin while NSpace
    // stayed in the background. Gating on isBrowserVisible() means we never
    // release focus based on an unreliable background poll; when the user
    // returns to the foreground a reliable poll drives the release if we are
    // genuinely idle.
    if (!wePlaying && focusHeld && BrowserFragment.isBrowserVisible()) {
      abandonFocus();
    }

    int state = paused ? PlaybackState.STATE_PAUSED : PlaybackState.STATE_PLAYING;
    PlaybackState pb = new PlaybackState.Builder()
        .setState(state, current, 1.0f)
        .setActions(PlaybackState.ACTION_PLAY
            | PlaybackState.ACTION_PAUSE
            | PlaybackState.ACTION_SEEK_TO
            | PlaybackState.ACTION_STOP)
        .build();
    session.setPlaybackState(pb);

    MediaMetadata md = new MediaMetadata.Builder()
        .putString(MediaMetadata.METADATA_KEY_TITLE, title)
        .build();
    session.setMetadata(md);

    // If playback has ended / been paused and no BrowserFragment instance is
    // alive, free the retained WebView (and its ~280MB renderer) and stop the
    // service so memory is reclaimed instead of lingering forever.
    // This is what keeps the head unit from feeling sluggish after browsing.
    //
    // NOTE: the check must be isBrowserAlive(), NOT isBrowserVisible().
    // "Visible" also goes false when the whole app is backgrounded via the
    // system Home key while the fragment stays alive; releasing (destroying)
    // the WebView in that state leaves the live fragment holding a destroyed
    // instance -- a dead page plus a "destroyed WebView" warning storm from
    // its volumeCheck poller when the user returns.
    if (!wePlaying && !BrowserFragment.isBrowserAlive()) {
      MediaWebViewHolder.getInstance().release();
      stopSelf();
      return;
    }

    // Promote to a foreground service (with a media notification) only while
    // audio is actually playing -- not merely because a page with a media
    // element is open. This avoids a stray notification on every browse.
    if (sPlaying || foreground) {
      enterForeground(title);
    }
  }

  /**
   * Passively detect external audio interruptions and auto-resume our playback.
   *
   * <p>Chromium's AudioFocusDelegate pauses (or ducks) our media when another
   * app grabs audio focus. For a permanent LOSS it abandons focus and keeps its
   * duck multiplier at 0, so after the other app stops our media is "playing"
   * but SILENT.
   *
   * <p>Detection: {@code isExternalAudioActive()} — resolved at runtime via
   * {@code AudioManager.getActivePlaybackConfigurations()} and looking at the
   * client UID of every ACTIVE playback config. If any active config belongs to
   * an app other than us, an external app is producing audio. This is far more
   * reliable than {@code isMusicActive()} (which is true while *we* play too,
   * and counting it as "external" re-latched the interruption on every resume,
   * making the auto-resume branch fire on our own playback → the play/pause/play
   * flapping seen after closing Douyin) AND it does not depend on the in-WebView
   * JS poll ({@code sPlaying}), which is throttled / stalls while the app is
   * backgrounded and previously caused a self-oscillation (resume → kick focus →
   * stale sPlaying=false → updateFromJson abandoned the focus → audio dropped →
   * isMusicActive() false → resume again). A phone / VoIP call also counts as an
   * external interruption while we were playing, so we resume after it ends.
   *
   * <p>Recovery: while latched we attempt resume only on the FALLING EDGE of the
   * external audio (the other app stopped) OR whenever NSPACE is the foreground
   * app. Crucially we no longer resume merely because {@code isMusicActive()} is
   * false — that fired on every tiny audio gap of our own stream in the
   * background and was the flapping source. Returning to the app (WebView
   * refocused) drives Chromium to resume the (possibly iframe) player and
   * reclaim focus. While the other app is still foreground we stay quiet.
   */
  private void checkExternalPlayback() {
    int mode = audioManager.getMode();
    boolean callMode = mode == AudioManager.MODE_IN_CALL
        || mode == AudioManager.MODE_IN_COMMUNICATION;
    boolean externalActive = isExternalAudioActive();
    if (callMode && wePlaying) {
      externalActive = true;
    }

    // Rising edge: an external app (or a phone / VoIP call) just grabbed audio
    // → latch an interruption and mark wePlaying=false (the external app now owns
    // the output; Chromium paused us on focus loss).
    if (externalActive && !wasExternalActive) {
      interruptedByExternal = true;
      kickRetries = RESUME_MAX_ATTEMPTS;
      lastResumeAttempt = 0; // allow an immediate first attempt
      kickSucceeded = false;
      wePlaying = false;
      Log.d(TAG, "external audio started, latched interruption (auto-resume armed)");
    }
    // Falling edge: the external app (or call) stopped → recover.
    boolean externalStopped = wasExternalActive && !externalActive;
    wasExternalActive = externalActive;

    if (interruptedByExternal) {
      boolean nspaceForeground = BrowserFragment.isBrowserVisible();
      if (externalStopped || nspaceForeground) {
        long now = System.currentTimeMillis();
        if (now - lastResumeAttempt >= RESUME_RETRY_MS) {
          Log.d(TAG, "auto-resume attempt ("
              + (RESUME_MAX_ATTEMPTS - kickRetries + 1) + "/" + RESUME_MAX_ATTEMPTS
              + ") ext=" + externalActive + " fg=" + nspaceForeground);
          resumePlayback(); // sets wePlaying=true, resets kickSucceeded
          lastResumeAttempt = now;
          kickRetries--;
          // Clear the latch as soon as the recovery kick has actually been
          // granted (sound restored) OR we have exhausted the budget. This
          // prevents an endless kick/abandon churn when the page cannot sustain
          // playback in the background (e.g. OS throttling) — once we have
          // kicked focus the recovery action is done; further retries would
          // only fight the system. The user can still resume manually.
          if (kickRetries <= 0 || (!externalActive && kickSucceeded)) {
            interruptedByExternal = false; // recovered or gave up
            Log.d(TAG, "auto-resume done (focus=" + focusHeld + " kick=" + kickSucceeded + ")");
          }
        }
      }
    }

    if (BuildConfig.DEBUG) {
      Log.d(TAG, "poll ext=" + externalActive + " music=" + audioManager.isMusicActive()
          + " sPlay=" + sPlaying + " wePlay=" + wePlaying + " focus=" + focusHeld
          + " latch=" + interruptedByExternal + " budget=" + kickRetries);
    }
  }

  /**
   * True if some app OTHER than us is actively playing audio. Resolved via
   * {@link AudioManager#getActivePlaybackConfigurations()} using reflection
   * (the offline compileSdk 34 stubs omit AudioPlaybackConfiguration symbols),
   * inspecting the client UID of each ACTIVE config. This cleanly separates
   * "we are playing" from "an external app is playing" without relying on
   * isMusicActive() (which conflates the two) or the background-unreliable JS
   * poll. Falls back to {@code isMusicActive() && !wePlaying} if the reflection
   * path is unavailable on a given device.
   */
  private boolean isExternalAudioActive() {
    try {
      if (sGetActivePlaybackConfigs == null) {
        sGetActivePlaybackConfigs = AudioManager.class.getMethod(
            "getActivePlaybackConfigurations");
        Class<?> cfgClass = Class.forName("android.media.AudioPlaybackConfiguration");
        sCfgIsActive = cfgClass.getMethod("isActive");
        sCfgGetClientUid = cfgClass.getMethod("getClientUid");
      }
      Object result = sGetActivePlaybackConfigs.invoke(audioManager);
      if (result instanceof List<?>) {
        for (Object cfg : (List<?>) result) {
          if (cfg == null) {
            continue;
          }
          boolean active = (Boolean) sCfgIsActive.invoke(cfg);
          if (!active) {
            continue;
          }
          int uid = (Integer) sCfgGetClientUid.invoke(cfg);
          if (uid != myUid) {
            return true; // another app is actively playing
          }
        }
      }
      return false;
    } catch (Throwable t) {
      // Fallback: system-wide music active while we believe we are not playing.
      return audioManager.isMusicActive() && !wePlaying;
    }
  }

  /**
   * Called by {@link BrowserFragment#onResume()} the moment the browser WebView
   * regains window focus. If an external interruption is latched we recover
   * immediately (the media key now reaches a focused WebView, so Chromium
   * resumes the player — including cross-origin iframes — and reclaims focus).
   * Also schedules a second attempt shortly after, in case the external app has
   * not fully released focus yet.
   */
  public static void onBrowserForeground() {
    if (sInstance != null) {
      sInstance.tryRecoverNow();
    }
  }

  private void tryRecoverNow() {
    if (!interruptedByExternal) {
      return;
    }
    Log.d(TAG, "browser foreground, immediate recovery attempt");
    resumePlayback();
    pollHandler.postDelayed(this::resumePlayback, 800);
    lastResumeAttempt = System.currentTimeMillis();
  }

  /** Resume media after an external-app interruption and restore sound. {@code
   *  play()} alone is not enough: Chromium abandoned focus on the interruption
   *  and keeps its duck volume multiplier at 0, so the media would be "playing"
   *  but silent. The focus kick makes Chromium receive AUDIOFOCUS_GAIN and
   *  reset the multiplier. */
  private void resumePlayback() {
    wePlaying = true; // we are (re)starting playback — suppresses the abandon
                      // guard and prevents our own audio from re-latching.
    kickSucceeded = false; // will be set true by the delayed kickAudioFocus
    // (1) Page-DOM element for same-origin players.
    MediaWebViewHolder.getInstance().play();
    // (2) Forward a media key so Chromium's native media session resumes
    // cross-origin iframe players (Stingray / Deezer / …) our JS cannot reach.
    MediaWebViewHolder.getInstance().dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY);
    MediaWebViewHolder.getInstance().setVolume(1.0f);
    // Delay the focus kick so it lands after the external app has fully
    // released audio focus; an immediate kick can be denied (or the external
    // app re-grabs focus), leaving Chromium's duck multiplier at 0 = silent.
    pollHandler.postDelayed(this::kickAudioFocus, KICK_DELAY_MS);
  }

  /** Request (and hold) audio focus so Chromium's AudioFocusDelegate receives
   *  AUDIOFOCUS_GAIN and resets its duck multiplier. The listener is a
   *  deliberate no-op: reacting to LOSS here re-triggered the v2.1.0.41/2.1.0.42
   *  ping-pong with Chromium's own internal focus client (same UID, two
   *  competing requests). We hold the focus until playback pauses. */
  private void kickAudioFocus() {
    if (audioManager == null || focusHeld) {
      return;
    }
    int res;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      AudioAttributes attrs = new AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_MEDIA)
          .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
          .build();
      focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
          .setAudioAttributes(attrs)
          .setOnAudioFocusChangeListener(kickListener)
          .setWillPauseWhenDucked(false)
          .build();
      res = audioManager.requestAudioFocus(focusRequest);
    } else {
      res = audioManager.requestAudioFocus(kickListener,
          AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
    }
    focusHeld = (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
    kickSucceeded = focusHeld;
    Log.d(TAG, "kickAudioFocus: " + (focusHeld ? "granted" : "denied"));
  }

  /** Abandon our held audio focus so other apps can take over the stream.
   *  Called when playback pauses / stops / the service is destroyed. */
  private void abandonFocus() {
    if (!focusHeld) {
      return;
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null) {
      audioManager.abandonAudioFocusRequest(focusRequest);
      focusRequest = null;
    } else {
      audioManager.abandonAudioFocus(kickListener);
    }
    focusHeld = false;
    kickSucceeded = false;
    Log.d(TAG, "abandonFocus: released");
  }

  private void enterForeground(String title) {
    createChannel();
    Notification n = buildNotification(title);
    startForeground(NOTIF_ID, n);
    foreground = true;
    Log.d(TAG, "entered foreground");
  }

  private void createChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      NotificationManager nm =
          (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
      if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
        NotificationChannel ch = new NotificationChannel(
            CHANNEL_ID, "NSpace Media", NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
      }
    }
  }

  private Notification buildNotification(String title) {
    Notification.Builder b;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      b = new Notification.Builder(this, CHANNEL_ID);
    } else {
      b = new Notification.Builder(this);
    }
    b.setContentTitle("NSpace")
        .setContentText(title)
        .setSmallIcon(R.drawable.ic_launcher)
        .setVisibility(Notification.VISIBILITY_PUBLIC)
        .setOngoing(true);

    Intent intent = new Intent(this, MainActivity.class);
    intent.setAction(Intent.ACTION_MAIN);
    intent.addCategory(Intent.CATEGORY_LAUNCHER);
    PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    b.setContentIntent(pi);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      Notification.MediaStyle style = new Notification.MediaStyle();
      style.setMediaSession(session.getSessionToken());
      b.setStyle(style);
    }
    return b.build();
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    return START_STICKY;
  }

  @Nullable
  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  @Override
  public void onDestroy() {
    pollHandler.removeCallbacks(pollRunnable);
    abandonFocus();
    if (session != null) {
      session.setActive(false);
      session.release();
    }
    sInstance = null;
    super.onDestroy();
    Log.d(TAG, "PlaybackService destroyed");
  }
}

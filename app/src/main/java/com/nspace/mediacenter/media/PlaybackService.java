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
import androidx.annotation.Nullable;
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
  private int kickRetries = 0;
  private boolean focusHeld = false;
  private AudioFocusRequest focusRequest;

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

    session = new MediaSession(this, "NSpaceMedia");
    session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS
        | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
    session.setCallback(new MediaSession.Callback() {
      private final Handler h = new Handler(Looper.getMainLooper());

      @Override
      public void onPlay() {
        h.post(() -> MediaWebViewHolder.getInstance().play());
      }

      @Override
      public void onPause() {
        interruptedByExternal = false;
        kickRetries = 0;
        abandonFocus();
        h.post(() -> MediaWebViewHolder.getInstance().pause());
      }

      @Override
      public void onSeekTo(long pos) {
        h.post(() -> MediaWebViewHolder.getInstance().seekTo(pos));
      }

      @Override
      public void onStop() {
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

    // On fresh user play Chromium manages its own focus, so we leave it alone.
    // When playback pauses / ends (including an external-app interruption that
    // paused us), release our held focus so we don't block the system stream.
    if (!sPlaying && focusHeld) {
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
    if (!sPlaying && !BrowserFragment.isBrowserAlive()) {
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
   * Passively detect external audio playback and auto-resume when it stops.
   *
   * <p>Chromium's AudioFocusDelegate pauses our media when another app (phone
   * call, navigation TTS, other music app) grabs audio focus. For transient
   * losses it auto-resumes, but for permanent LOSS (e.g. phone call) it
   * abandons focus entirely — playback stays paused forever after the call.
   *
   * <p>This method runs every second from the poll loop. When we're NOT
   * playing (sPlaying == false), it checks {@code isMusicActive()} (catches
   * other music apps) and {@code getMode()} (catches phone calls via
   * MODE_IN_CALL / MODE_IN_COMMUNICATION). If external audio is detected and
   * we were recently playing, we set {@code shouldAutoResume}. When the
   * external audio stops, we call {@code play()} to resume.
   *
   * <p>We only check when NOT playing because isMusicActive() returns true
   * when our own media is active — we can only reliably detect external
   * audio when our media is paused.
   */
  private void checkExternalPlayback() {
    int mode = audioManager.getMode();
    boolean externalActive = audioManager.isMusicActive()
        || mode == AudioManager.MODE_IN_CALL
        || mode == AudioManager.MODE_IN_COMMUNICATION;

    if (externalActive && !wasExternalActive) {
      // External audio just appeared. If we were playing recently, mark for
      // resume + focus kick when it stops.
      if (System.currentTimeMillis() - lastPlayingTime < 5000) {
        interruptedByExternal = true;
        kickRetries = 3;
        Log.d(TAG, "external audio detected, will resume+kick when it stops");
      }
    } else if (!externalActive && wasExternalActive) {
      // External audio just stopped. Resume our media and kick focus so the
      // sound actually comes back (otherwise Chromium stays silently ducked).
      if (interruptedByExternal) {
        Log.d(TAG, "external audio stopped, resuming playback + kicking focus");
        resumePlayback();
      }
    }
    wasExternalActive = externalActive;

    // Safety net: if we are flagged interrupted but (for any reason) the
    // resume-kick never fired, or the user returned to the foreground with the
    // media "playing but silent", re-kick a few times while we are actually
    // playing and no external audio is active.
    if (interruptedByExternal && sPlaying && !externalActive && kickRetries > 0) {
      kickAudioFocus();
      kickRetries--;
      if (kickRetries <= 0) {
        interruptedByExternal = false;
      }
    }
  }

  /** Resume media after an external-app interruption and restore sound. {@code
   *  play()} alone is not enough: Chromium abandoned focus on the interruption
   *  and keeps its duck volume multiplier at 0, so the media would be "playing"
   *  but silent. The focus kick makes Chromium receive AUDIOFOCUS_GAIN and
   *  reset the multiplier. */
  private void resumePlayback() {
    MediaWebViewHolder.getInstance().play();
    MediaWebViewHolder.getInstance().setVolume(1.0f);
    kickAudioFocus();
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

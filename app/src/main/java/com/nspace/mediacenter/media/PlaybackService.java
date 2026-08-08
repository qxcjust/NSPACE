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

  // ── Audio focus management ─────────────────────────────────────
  // The WebView's Chromium engine grabs audio focus automatically, but it does
  // NOT respond to focus-loss events from other apps (e.g. navigation voice
  // guidance). Without our own AudioFocus listener the music keeps playing at
  // full volume over the navigation prompts. We request focus ourselves and
  // duck / pause in response to transient losses, then abandon when playback
  // stops so other apps can reclaim it.
  private AudioManager audioManager;
  private AudioFocusRequest focusRequest;
  private boolean focusHeld = false;
  private boolean wasDucked = false;
  private boolean pausedByFocusLoss = false;

  private final AudioManager.OnAudioFocusChangeListener focusListener =
      new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int focusChange) {
          switch (focusChange) {
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
              // Another app needs the audio briefly and can tolerate us
              // continuing at a lower volume (e.g. navigation TTS).
              wasDucked = true;
              MediaWebViewHolder.getInstance().setVolume(0.2f);
              Log.d(TAG, "audio focus: duck (transient-can-duck)");
              break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
              // Another app needs exclusive audio for a short time. Pause and
              // remember to resume when focus returns.
              wasDucked = false;
              pausedByFocusLoss = true;
              MediaWebViewHolder.getInstance().pause();
              Log.d(TAG, "audio focus: pause (transient loss)");
              break;
            case AudioManager.AUDIOFOCUS_LOSS:
              // Permanent loss — another app took over. Stop and release.
              wasDucked = false;
              pausedByFocusLoss = false;
              abandonAudioFocus();
              MediaWebViewHolder.getInstance().release();
              stopSelf();
              Log.d(TAG, "audio focus: permanent loss, releasing");
              break;
            case AudioManager.AUDIOFOCUS_GAIN:
              // Focus restored — undo ducking or resume playback if we paused.
              if (wasDucked) {
                wasDucked = false;
                MediaWebViewHolder.getInstance().setVolume(1.0f);
                Log.d(TAG, "audio focus: restored volume after duck");
              }
              if (pausedByFocusLoss) {
                pausedByFocusLoss = false;
                MediaWebViewHolder.getInstance().play();
                Log.d(TAG, "audio focus: resumed after transient loss");
              }
              break;
          }
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
        pollHandler.postDelayed(this, 1000);
      }
    };
    pollHandler.postDelayed(pollRunnable, 1000);

    sInstance = this;
    Log.d(TAG, "PlaybackService created");
  }

  /**
   * Request audio focus so we can duck or pause when another app (navigation
   * voice guidance, phone call, etc.) needs the audio stream. Uses the modern
   * {@link AudioFocusRequest} on API 26+ and the legacy API below.
   *
   * @return true if focus was granted.
   */
  private boolean requestAudioFocus() {
    if (audioManager == null || focusHeld) {
      return focusHeld;
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      AudioAttributes attrs = new AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_MEDIA)
          .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
          .build();
      focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
          .setAudioAttributes(attrs)
          .setOnAudioFocusChangeListener(focusListener)
          .setWillPauseWhenDucked(false)
          .build();
      int res = audioManager.requestAudioFocus(focusRequest);
      focusHeld = (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
    } else {
      int res = audioManager.requestAudioFocus(focusListener,
          AudioManager.STREAM_MUSIC,
          AudioManager.AUDIOFOCUS_GAIN);
      focusHeld = (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
    }
    Log.d(TAG, "requestAudioFocus: " + (focusHeld ? "granted" : "denied"));
    return focusHeld;
  }

  /** Abandon audio focus so other apps can use the audio stream. */
  private void abandonAudioFocus() {
    if (!focusHeld) {
      return;
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null) {
      audioManager.abandonAudioFocusRequest(focusRequest);
      focusRequest = null;
    } else {
      audioManager.abandonAudioFocus(focusListener);
    }
    focusHeld = false;
    wasDucked = false;
    pausedByFocusLoss = false;
    Log.d(TAG, "abandonAudioFocus: released");
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

    boolean wasPlaying = sPlaying;
    sPlaying = !paused;

    // Request audio focus when playback starts, abandon when it stops.
    // This lets us duck/pause for navigation voice guidance and other
    // transient audio from other apps — Chromium's internal focus handling
    // does NOT react to these events on its own.
    if (sPlaying && !wasPlaying) {
      requestAudioFocus();
    } else if (!sPlaying && wasPlaying) {
      abandonAudioFocus();
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
    abandonAudioFocus();
    if (session != null) {
      session.setActive(false);
      session.release();
    }
    sInstance = null;
    super.onDestroy();
    Log.d(TAG, "PlaybackService destroyed");
  }
}

package com.nspace.mediacenter.util;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.concurrent.TimeUnit;

/**
 * Free-trial gate for the open (no-VIN) build.
 *
 * <p>The app is fully usable with no authorization step, but only for a fixed
 * window measured from the first launch. After the window elapses the host
 * activity locks the UI.
 *
 * <p>Anti-rollback: a user can't extend the trial by setting the device clock
 * backwards. We persist the latest "observed now" and never let it move
 * earlier, so {@code observed - start} keeps advancing in real elapsed time.
 * (This is a light deterrent, not a hard security boundary — a rooted device
 * can still clear app data to reset the trial.)
 */
public final class TrialLimiter {

  private static final String PREFS = "nspace_trial";
  private static final String KEY_START = "trial_start_ms";
  private static final String KEY_LAST = "last_seen_ms";

  /** Trial length: 7 days. */
  public static final long TRIAL_DURATION_MS = TimeUnit.DAYS.toMillis(7);

  private TrialLimiter() {}

  /**
   * @return {@code true} if the trial window has elapsed and the app should
   *     lock. On the very first call it anchors the trial start and returns
   *     {@code false} (the trial is just beginning).
   */
  public static boolean isExpired(Context ctx) {
    SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    long now = System.currentTimeMillis();

    long start = sp.getLong(KEY_START, 0L);
    if (start == 0L) {
      // First launch: anchor the trial window now.
      sp.edit()
          .putLong(KEY_START, now)
          .putLong(KEY_LAST, now)
          .apply();
      return false;
    }

    // Monotonic "now": never move the observed clock backwards.
    long last = sp.getLong(KEY_LAST, now);
    long observed = Math.max(last, now);
    if (observed != last) {
      sp.edit().putLong(KEY_LAST, observed).apply();
    }

    return (observed - start) > TRIAL_DURATION_MS;
  }

  /** Milliseconds left in the trial (clamped to 0). */
  public static long getRemainingMs(Context ctx) {
    SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    long now = System.currentTimeMillis();
    long start = sp.getLong(KEY_START, 0L);
    if (start == 0L) {
      return TRIAL_DURATION_MS;
    }
    long last = sp.getLong(KEY_LAST, now);
    long observed = Math.max(last, now);
    return Math.max(0L, TRIAL_DURATION_MS - (observed - start));
  }
}

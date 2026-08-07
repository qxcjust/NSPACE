package com.nspace.mediacenter.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import java.util.UUID;

/**
 * Stable, app-local device identifier for authorization.
 *
 * <p>We deliberately do NOT use any OEM-supplied hardware id (VIN / serial /
 * IMEI): those vary by manufacturer, are often unavailable to a normal (non-
 * privileged) app on Android 10+, and (where readable) are trivially spoofable
 * by a rooted device. Instead we use {@link Settings.Secure#ANDROID_ID}, which
 * is available to every app with no permission, stays constant across app
 * reinstalls and data clears, and only changes on factory reset.
 *
 * <p>ANDROID_ID is scoped per-app-per-user on Android 8+, which is exactly what
 * we want (a stable identity for NSPACE specifically). A few low-quality ROMs
 * return the well-known constant {@code 9774d56d682e549c} for every device, so
 * in that (and any null/empty) case we fall back to a random UUID persisted in
 * private storage — generated once and stable for the app's lifetime on that
 * device.
 */
public final class DeviceId {

  private static final String PREFS = "nspace_device";
  private static final String KEY_FALLBACK_UUID = "fallback_uuid";
  // Known non-unique constant returned by some emulators / broken ROMs.
  private static final String ANDROID_ID_CONSTANT = "9774d56d682e549c";

  private DeviceId() {
  }

  /** The device id used as the authorization file name on GitHub Pages. */
  public static String getDeviceId(Context ctx) {
    String id = null;
    try {
      id = Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.ANDROID_ID);
    } catch (Exception e) {
      id = null;
    }
    if (id == null || id.isEmpty() || id.equals(ANDROID_ID_CONSTANT)) {
      return getFallbackUuid(ctx);
    }
    return id;
  }

  private static synchronized String getFallbackUuid(Context ctx) {
    SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    String uuid = sp.getString(KEY_FALLBACK_UUID, null);
    if (uuid == null) {
      uuid = UUID.randomUUID().toString();
      sp.edit().putString(KEY_FALLBACK_UUID, uuid).apply();
    }
    return uuid;
  }
}

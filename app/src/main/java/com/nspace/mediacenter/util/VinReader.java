package com.nspace.mediacenter.util;

import android.annotation.SuppressLint;
import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Reads the vehicle VIN from the car head unit.
 *
 * <p>On the GXATEK / SynCore (Android Automotive) units we target, the VIN is
 * published in system properties rather than via the {@code android.car}
 * {@code VehiclePropertyManager} API (which requires system privileges our
 * third-party app does not have). The public, world-readable property is
 * {@code sys.vehicle.hardware.vin.code} (SELinux type
 * {@code system_gxatek_public_prop}); the same value is also mirrored in
 * {@code persist.sys.vehicle.vin} and {@code persist.sys.vehicle.vin.code}.
 *
 * <p>Reading is attempted in two ways, in order, returning the first non-empty
 * result:
 * <ol>
 *   <li>via the hidden {@code android.os.SystemProperties} class (reflection);
 *   <li>via a {@code getprop} shell exec as a fallback.
 * </ol>
 */
public final class VinReader {

  private static final String TAG = "VinReader";

  // Ordered by reliability: the *_public_prop one is intentionally world-readable.
  private static final String[] KEYS = {
      "sys.vehicle.hardware.vin.code",
      "persist.sys.vehicle.vin",
      "persist.sys.vehicle.vin.code",
  };

  private static String cachedVin;

  private VinReader() {
  }

  /**
   * Returns the VIN (e.g. {@code HACRA1B30S1092845}), or {@code null} if it
   * cannot be read (no vehicle data / blocked by SELinux). The result is cached
   * after the first successful read so repeated callers don't re-hit reflection
   * or spawn a process.
   */
  public static String getVin() {
    if (cachedVin != null) {
      return cachedVin;
    }
    for (String key : KEYS) {
      String v = readKey(key);
      if (v != null && !v.isEmpty()) {
        cachedVin = v.trim().toUpperCase(java.util.Locale.US);
        Log.d(TAG, "VIN resolved from property '" + key + "': " + cachedVin);
        return cachedVin;
      }
    }
    Log.d(TAG, "VIN not available from any known property");
    return null;
  }

  @SuppressLint("PrivateApi")
  private static String readKey(String key) {
    String v = readViaSystemProperties(key);
    if (v != null && !v.isEmpty()) {
      return v;
    }
    return readViaGetprop(key);
  }

  @SuppressLint("PrivateApi")
  private static String readViaSystemProperties(String key) {
    try {
      Class<?> cls = Class.forName("android.os.SystemProperties");
      Method get = cls.getMethod("get", String.class, String.class);
      Object result = get.invoke(null, key, "");
      return result instanceof String ? (String) result : null;
    } catch (Throwable t) {
      // Hidden API blocked, or method signature differs — fall through.
      return null;
    }
  }

  private static String readViaGetprop(String key) {
    try {
      Process p = Runtime.getRuntime().exec(new String[] { "getprop", key });
      try (BufferedReader br = new BufferedReader(
          new InputStreamReader(p.getInputStream()))) {
        String line = br.readLine();
        return line != null ? line : "";
      } finally {
        p.destroy();
      }
    } catch (Throwable t) {
      return null;
    }
  }

  /** Exposed only for diagnostics / unit testing. */
  static String[] getKeysForTest() {
    return Arrays.copyOf(KEYS, KEYS.length);
  }
}

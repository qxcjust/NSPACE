package com.nspace.mediacenter.util;

import android.content.Context;
import com.nspace.mediacenter.config.RegionAppsConfig;
import com.nspace.mediacenter.util.AppIntegrity;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * Device-binding gate. The app can be bound to a single car unit by VIN,
 * configured in the encrypted {@code region_apps_config.enc} asset
 * ({@code bound_vin}). On launch we compare the live VIN (see {@link VinReader})
 * against the bound value.
 *
 * <ul>
 *   <li>{@code NO_BINDING}       – config has no bound_vin; app runs unrestricted.</li>
 *   <li>{@code OK}                – live VIN matches the bound VIN; authorized.</li>
 *   <li>{@code LOCKED_MISMATCH}   – VIN present but differs; unauthorised device.</li>
 *   <li>{@code LOCKED_UNREADABLE} – bound VIN set but live VIN could not be read; fail-closed.</li>
 * </ul>
 */
public final class DeviceBinder {

  public enum Result { OK, NO_BINDING, LOCKED_MISMATCH, LOCKED_UNREADABLE }

  private DeviceBinder() {
  }

  /**
   * Evaluate the binding for the current device.
   */
  public static Result check(Context ctx) {
    // Tamper gate: a repackaged or debugged binary must never be authorized.
    if (!AppIntegrity.verify(ctx)) {
      return Result.LOCKED_MISMATCH;
    }
    RegionAppsConfig cfg = RegionAppsConfig.getInstance(ctx);
    String boundHash = (cfg != null) ? cfg.getBoundVinHash() : null;
    if (boundHash == null || boundHash.isEmpty()) {
      return Result.NO_BINDING;
    }
    String current = VinReader.getVin();
    if (current == null || current.isEmpty()) {
      // We cannot confirm this is the bound car, so fail closed.
      return Result.LOCKED_UNREADABLE;
    }
    // Compare the SHA-256 of the live VIN against the stored hash. The raw VIN is
    // never present in the config, and the comparison is constant-time.
    String currentHash = sha256Hex(current.toUpperCase(Locale.US));
    return constantTimeEquals(currentHash, boundHash) ? Result.OK : Result.LOCKED_MISMATCH;
  }

  /** SHA-256 of {@code s}, hex-encoded, lowercase. */
  private static String sha256Hex(String s) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] d = md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(d.length * 2);
      for (byte b : d) sb.append(String.format("%02x", b));
      return sb.toString();
    } catch (Exception e) {
      return "";
    }
  }

  /** Constant-time string comparison to avoid hash-timing side channels. */
  private static boolean constantTimeEquals(String a, String b) {
    if (a == null || b == null) return false;
    byte[] ba = a.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    byte[] bb = b.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    if (ba.length != bb.length) return false;
    int r = 0;
    for (int i = 0; i < ba.length; i++) r |= (ba[i] ^ bb[i]);
    return r == 0;
  }

  /** True when the app is allowed to run on this device. */
  public static boolean isAuthorized(Context ctx) {
    Result r = check(ctx);
    return r == Result.OK || r == Result.NO_BINDING;
  }
}

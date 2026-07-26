package com.nspace.mediacenter.util;

import android.content.Context;
import com.nspace.mediacenter.config.RegionAppsConfig;

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
    RegionAppsConfig cfg = RegionAppsConfig.getInstance(ctx);
    String bound = (cfg != null) ? cfg.getBoundVin() : null;
    if (bound == null || bound.isEmpty()) {
      return Result.NO_BINDING;
    }
    String current = VinReader.getVin();
    if (current == null || current.isEmpty()) {
      // We cannot confirm this is the bound car, so fail closed.
      return Result.LOCKED_UNREADABLE;
    }
    return current.equalsIgnoreCase(bound.trim()) ? Result.OK : Result.LOCKED_MISMATCH;
  }

  /** True when the app is allowed to run on this device. */
  public static boolean isAuthorized(Context ctx) {
    Result r = check(ctx);
    return r == Result.OK || r == Result.NO_BINDING;
  }
}

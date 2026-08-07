package com.nspace.mediacenter.util;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Local cache of the last successful remote authorization, used only in the
 * "any car may install, publish the VIN on GitHub Pages to authorize" mode
 * ({@code bound_vin_hash} is empty in the config).
 *
 * <p>Why this exists: when authorization depends entirely on the remote
 * allowlist, an offline device cannot prove it is authorized. To avoid
 * locking out a legitimately-authorized car that is temporarily offline (a
 * tunnel, no cellular signal), we remember the last online {@code PASS} for
 * this specific VIN. A car that was never authorized online is still locked
 * when offline — so an unauthorized install cannot sneak in just by going
 * offline.
 *
 * <p>This is a UX grace only; it is not a security boundary. The real
 * anti-tamper / anti-repackaging boundary remains {@link AppIntegrity}, and
 * the remote allowlist (GitHub Pages) is the source of truth. A {@code REVOKED}
 * result clears the cached grant so a revoked car cannot keep working offline
 * forever.
 */
public final class AuthorizationCache {

  private static final String PREFS = "nspace_auth";
  private static final String KEY_GRANTED_VIN = "granted_vin";

  private AuthorizationCache() {
  }

  /** Record that the given VIN just passed the remote authorization check. */
  public static void grant(Context ctx, String vin) {
    if (ctx == null || vin == null) return;
    prefs(ctx).edit().putString(KEY_GRANTED_VIN, vin).apply();
  }

  /** Forget any cached grant (called on a definitive REVOKED). */
  public static void revoke(Context ctx) {
    if (ctx == null) return;
    prefs(ctx).edit().remove(KEY_GRANTED_VIN).apply();
  }

  /**
   * True when this exact VIN previously passed an online authorization and has
   * not been revoked since. Used to let an offline-but-once-authorized car in.
   */
  public static boolean hasGrant(Context ctx, String vin) {
    if (ctx == null || vin == null || vin.isEmpty()) return false;
    String granted = prefs(ctx).getString(KEY_GRANTED_VIN, "");
    return vin.equalsIgnoreCase(granted);
  }

  private static SharedPreferences prefs(Context ctx) {
    return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
  }
}

package com.nspace.mediacenter.util;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;
import android.os.Debug;
import com.nspace.mediacenter.BuildConfig;
import java.security.MessageDigest;

/**
 * Self-integrity / tamper detection.
 *
 * <p>An attacker who repackages the APK with their own signing key (the standard
 * way to defeat the VIN binding / config encryption) will produce a build whose
 * signing certificate no longer matches the official one. This class verifies
 * the app's own signing-certificate SHA-256 against an embedded expected value,
 * and refuses to run when the binary has been tampered with, is being debugged,
 * or was built as a debuggable artifact.
 *
 * <p>It is invoked from multiple independent entry points (the launched
 * Activity, the device-binding gate, the config loader) so that patching out a
 * single call site in a decompiler is not sufficient to bypass it.
 */
public final class AppIntegrity {

  /**
   * SHA-256 (hex) of the official release signing certificate — NOT the signing
   * key itself. Computed at sign time and embedded here. If the signing
   * certificate is ever rotated, this value must be regenerated (see
   * {@code _gen_secrets.py}) or every release build will fail the check.
   */
  private static final String EXPECTED_CERT_SHA256 =
      "843e77aa8112f26bfe14a9de31f1cb659650481d1edb900533315f1dcb73f1e1";

  private static volatile Boolean sCached;

  private AppIntegrity() {
  }

  /** Returns {@code true} only when the running binary is genuine and untampered. */
  public static boolean verify(Context ctx) {
    if (sCached != null) return sCached;
    sCached = doVerify(ctx);
    return sCached;
  }

  private static boolean doVerify(Context ctx) {
    if (ctx == null) return false;

    // 1) Reject debug artifacts and live debuggers.
    if (BuildConfig.DEBUG) return false;
    if (Debug.isDebuggerConnected()) return false;

    // 2) Verify the signing certificate against the expected hash.
    try {
      PackageManager pm = ctx.getPackageManager();
      PackageInfo pi;
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        pi = pm.getPackageInfo(ctx.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
        SigningInfo si = pi.signingInfo;
        if (si == null) return false;
        if (!matchesAny(si.getApkContentsSigners())) return false;
      } else {
        // API 24–27: deprecated but still valid for our minSdk.
        pi = pm.getPackageInfo(ctx.getPackageName(), PackageManager.GET_SIGNATURES);
        if (!matchesAny(pi.signatures)) return false;
      }
      return true;
    } catch (Exception e) {
      // Any failure to read/verify the signature => treat as tampered.
      return false;
    }
  }

  private static boolean matchesAny(Signature[] sigs) {
    if (sigs == null) return false;
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      for (Signature sig : sigs) {
        String h = toHex(md.digest(sig.toByteArray()));
        if (EXPECTED_CERT_SHA256.equalsIgnoreCase(h)) {
          return true;
        }
      }
    } catch (Exception ignored) {
      return false;
    }
    return false;
  }

  private static String toHex(byte[] b) {
    StringBuilder sb = new StringBuilder(b.length * 2);
    for (byte x : b) sb.append(String.format("%02x", x));
    return sb.toString();
  }
}

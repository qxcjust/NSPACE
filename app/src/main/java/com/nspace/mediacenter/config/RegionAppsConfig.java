package com.nspace.mediacenter.config;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import com.nspace.mediacenter.util.KeyVault;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Region-based app ranking configuration loader.
 *
 * <p>Parses the encrypted asset {@code assets/region_apps_config.enc}, which is the
 * AES-256-GCM ciphertext of {@code region_apps_config.json}. The plaintext JSON is
 * NOT bundled in the APK — only the ciphertext ships. See {@code scripts/encrypt_config.js}
 * for how to (re)generate the asset from the source file in {@code config/}.
 *
 * <p>GCM is authenticated encryption: any tampering with the ciphertext (e.g. someone
 * unpacking the APK and editing the blob) makes decryption throw, so the app rejects a
 * modified config instead of silently trusting it and falls back to the built-in defaults.
 *
 * <p>Provides:
 * <ul>
 *   <li>Full region → app mapping (audio Top5 + video Top5)</li>
 *   <li>App metadata: URL, category, icon</li>
 *   <li>Convenience methods to resolve shortcuts for a given country code</li>
 * </ul>
 */
public final class RegionAppsConfig {

  private static final String TAG = "RegionAppsConfig";

  /** Metadata for a single app entry from the config. */
  public static final class AppInfo {
    public final String name;
    public final String category;       // "audio" | "video"
    public final String url;
    public final String icon;           // drawable resource name, or "" when absent
    public final int rank;              // 1-based within its category for the region

    AppInfo(String name, String category, String url, String icon, int rank) {
      this.name = name;
      this.category = category;
      this.url = url;
      this.icon = (icon != null) ? icon : "";
      this.rank = rank;
    }

    @Override
    public String toString() {
      return "AppInfo{" + name + " [" + category + " #" + rank + "] " + url + "}";
    }
  }

  /** Data for one target country/region. */
  public static final class RegionInfo {
    public final String code;           // ISO 3166-1 alpha-2 e.g. "TH", "KR"
    public final String name;           // native name e.g. "泰国"
    public final String nameEn;         // English name e.g. "Thailand"
    public final String continent;      // e.g. "东南亚", "西欧"
    public final List<AppInfo> audioTop5;
    public final List<AppInfo> videoTop5;

    RegionInfo(String code, String name, String nameEn, String continent,
               List<AppInfo> audioTop5, List<AppInfo> videoTop5) {
      this.code = code;
      this.name = name;
      this.nameEn = nameEn;
      this.continent = continent;
      this.audioTop5 = Collections.unmodifiableList(audioTop5);
      this.videoTop5 = Collections.unmodifiableList(videoTop5);
    }

    /** All 10 apps (audio first, then video), deduplicated by name. */
    public List<AppInfo> getAllAppsDeduped() {
      List<AppInfo> result = new ArrayList<>();
      java.util.Set<String> seen = new java.util.HashSet<>();
      for (AppInfo a : audioTop5) { if (seen.add(a.name)) result.add(a); }
      for (AppInfo a : videoTop5) { if (seen.add(a.name)) result.add(a); }
      return result;
    }
  }

  // ── Singleton ──────────────────────────────────────────────

  private static volatile RegionAppsConfig sInstance;

  private final JSONObject rawRoot;
  private final JSONObject appsIndex;   // name → app metadata

  private RegionAppsConfig(JSONObject root) throws Exception {
    this.rawRoot = root;
    this.appsIndex = root.optJSONObject("apps");
  }

  /**
   * Load (or return cached) config from the encrypted asset
   * {@code assets/region_apps_config.enc}.
   *
   * @return the config, or {@code null} if the asset is missing, cannot be decrypted,
   *         or is malformed. Callers MUST null-check; parsing failures must never crash
   *         the app — it falls back to the built-in {@code SHORTCUTS} in HomeFragment.
   */
  public static RegionAppsConfig getInstance(Context ctx) {
    if (sInstance != null) return sInstance;
    synchronized (RegionAppsConfig.class) {
      if (sInstance == null) {
        try {
          byte[] enc = readAssetBytes(ctx, "region_apps_config.enc");
          String json = decrypt(enc);
          sInstance = new RegionAppsConfig(new JSONObject(json));
        } catch (Exception e) {
          Log.e(TAG, "Failed to decrypt region_apps_config.enc, falling back", e);
          return null;
        }
      }
    }
    return sInstance;
  }

  // ── Public accessors ───────────────────────────────────────

  /** Return all known region codes (ISO alpha-2). */
  public List<String> getRegionCodes() {
    JSONObject regions = rawRoot.optJSONObject("regions");
    if (regions == null) return Collections.emptyList();
    List<String> codes = new ArrayList<>();
    java.util.Iterator<String> it = regions.keys();
    while (it.hasNext()) codes.add(it.next());
    Collections.sort(codes);
    return codes;
  }

  /**
   * Full region data by ISO code, or {@code null} if unknown.
   */
  public RegionInfo getRegion(String countryCode) {
    if (TextUtils.isEmpty(countryCode)) return null;
    JSONObject regions = rawRoot.optJSONObject("regions");
    if (regions == null) return null;
    JSONObject r = regions.optJSONObject(countryCode.toUpperCase(Locale.US));
    if (r == null) return null;

    String code = countryCode.toUpperCase(Locale.US);
    String name = r.optString("name", "");
    String nameEn = r.optString("nameEn", "");
    String continent = r.optString("continent", "");

    List<AppInfo> audio = parseAppList(r.optJSONArray("audioTop5"), "audio");
    List<AppInfo> video = parseAppList(r.optJSONArray("videoTop5"), "video");

    return new RegionInfo(code, name, nameEn, continent, audio, video);
  }

  /**
   * Convenience: resolve region from current system locale's country,
   * falling back to the supplied {@code defaultCode}.
   *
   * @return region info or {@code null} if the country has no entry.
   */
  public RegionInfo getRegionForCurrentLocale(Context ctx, String defaultCode) {
    String country = Locale.getDefault().getCountry();
    RegionInfo info = getRegion(country);
    if (info != null) return info;
    return getRegion(defaultCode);
  }

  /**
   * Returns the SHA-256 (hex) of the VIN this build is bound to, or {@code null}
   * if device-binding is not configured (the app then runs unbound). The raw VIN
   * is never stored in the config — only its hash — so it cannot be read back out
   * of the encrypted asset. {@link com.nspace.mediacenter.util.DeviceBinder}
   * compares this against the SHA-256 of the live VIN.
   */
  public String getBoundVinHash() {
    if (!rawRoot.has("bound_vin_hash")) return null;
    String v = rawRoot.optString("bound_vin_hash", "");
    return v.isEmpty() ? null : v;
  }

  /**
   * Get raw app metadata by name (brand color, URL, etc.).
   * Returns {@code null} if the app is not in the catalog.
   */
  public AppInfo getAppInfo(String appName) {
    if (appsIndex == null || TextUtils.isEmpty(appName)) return null;
    JSONObject a = appsIndex.optJSONObject(appName);
    if (a == null) return null;
    return new AppInfo(
        appName,
        a.optString("category", "video"),
        a.optString("url", ""),
        a.optString("icon", ""),
        0
    );
  }

  // ── Encrypted asset loading (AES-256-GCM) ─────────────────

  private static final int GCM_IV_LEN = 12;       // recommended GCM IV length
  private static final int GCM_TAG_BITS = 128;    // GCM authentication tag length

  /** Read a whole asset into a byte array. */
  private static byte[] readAssetBytes(Context ctx, String filename) throws Exception {
    InputStream is = ctx.getAssets().open(filename);
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    byte[] buf = new byte[8192];
    int n;
    while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
    is.close();
    return bos.toByteArray();
  }

  /**
   * Decrypt an AES-256-GCM asset. On-disk layout is:
   * <pre>[ 12-byte IV ][ ciphertext ][ 16-byte auth tag ]</pre>
   * The auth tag lets {@link Cipher} detect tampering/bit-rot: altering the file makes
   * {@link Cipher#doFinal(byte[])} throw {@code AEADBadTagException}.
   */
  private static String decrypt(byte[] data) throws Exception {
    if (data == null || data.length <= GCM_IV_LEN + (GCM_TAG_BITS / 8)) {
      throw new IllegalArgumentException("config blob too short");
    }
    byte[] iv = Arrays.copyOfRange(data, 0, GCM_IV_LEN);
    byte[] ct = Arrays.copyOfRange(data, GCM_IV_LEN, data.length);
    byte[] key = KeyVault.configKey();
    SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_BITS, iv));
    byte[] plain = cipher.doFinal(ct);
    return new String(plain, StandardCharsets.UTF_8);
  }

  private static byte[] hexToBytes(String s) {
    int len = s.length();
    if ((len % 2) != 0) throw new IllegalArgumentException("hex key length must be even");
    byte[] out = new byte[len / 2];
    for (int i = 0; i < out.length; i++) {
      out[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
    }
    return out;
  }

  // ── Internal helpers ───────────────────────────────────────

  private List<AppInfo> parseAppList(JSONArray arr, String category) {
    List<AppInfo> list = new ArrayList<>();
    if (arr == null) return list;
    for (int i = 0; i < arr.length(); i++) {
      String name = arr.optString(i, null);
      if (name == null) continue;
      AppInfo meta = getAppInfo(name);
      String url   = (meta != null) ? meta.url : "";
      list.add(new AppInfo(name, category, url, (meta != null) ? meta.icon : "", i + 1));
    }
    return list;
  }
}

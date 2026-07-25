package com.nspace.mediacenter.config;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Region-based app ranking configuration loader.
 *
 * <p>Parses {@code assets/region_apps_config.json} which contains top-5 audio/video
 * apps per target country (the "出海" / go-global market list). Provides:
 * <ul>
 *   <li>Full region → app mapping (audio Top5 + video Top5)</li>
 *   <li>App metadata: URL, category, icon</li>
 *   <li>Convenience methods to resolve shortcuts for a given country code</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * RegionAppsConfig config = RegionAppsConfig.getInstance(context);
 * List<RegionAppsConfig.AppInfo> apps = config.getTopAppsForRegion("TH");
 * }</pre>
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
   * Load (or return cached) config from {@code assets/region_apps_config.json}.
   *
   * @return the config, or {@code null} if the asset is missing or malformed.
   *         Callers MUST null-check; parsing failures must never crash the app.
   */
  public static RegionAppsConfig getInstance(Context ctx) {
    if (sInstance != null) return sInstance;
    synchronized (RegionAppsConfig.class) {
      if (sInstance == null) {
        try {
          String json = loadAsset(ctx, "region_apps_config.json");
          sInstance = new RegionAppsConfig(new JSONObject(json));
        } catch (Exception e) {
          Log.e(TAG, "Failed to load region_apps_config.json, falling back", e);
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

  // ── Internal helpers ───────────────────────────────────────

  private static String loadAsset(Context ctx, String filename) throws Exception {
    InputStream is = ctx.getAssets().open(filename);
    byte[] data = new byte[is.available()];
    int total = 0;
    while (total < data.length) {
      int n = is.read(data, total, data.length - total);
      if (n < 0) break;
      total += n;
    }
    is.close();
    return new String(data, 0, total, StandardCharsets.UTF_8);
  }

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

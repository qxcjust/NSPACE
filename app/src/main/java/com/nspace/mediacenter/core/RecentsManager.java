package com.nspace.mediacenter.core;

import com.nspace.mediacenter.model.RecentItem;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Tracks recently opened pages (with a captured snapshot) for the home screen's
 * "Continue Playing" row. Persisted as a JSON array in MMKV, mirroring the
 * approach used by {@link HistoryManager}.
 *
 * <p>Entries are grouped by app/domain (re-opening any page of the same app
 * moves that app's entry to the top and refreshes its snapshot, so a single app
 * never appears twice) and capped at {@link #MAX_ENTRIES} (the 3 most recent
 * apps).
 */
public final class RecentsManager {

  private static final RecentsManager INSTANCE = new RecentsManager();
  private static final String KEY_RECENTS = "nspace.recents.v1";
  private static final int MAX_ENTRIES = 3;

  private final List<RecentItem> recents = new ArrayList<>();

  private RecentsManager() {
    load();
  }

  /**
   * Returns the singleton recents manager.
   *
   * @return the shared instance
   */
  public static RecentsManager getInstance() {
    return INSTANCE;
  }

  private void load() {
    String raw = StorageManager.getInstance().getString(KEY_RECENTS, "[]");
    recents.clear();
    try {
      JSONArray array = new JSONArray(raw);
      for (int i = 0; i < array.length(); i++) {
        JSONObject object = array.getJSONObject(i);
        recents.add(new RecentItem(
            object.getString("id"),
            object.optString("title", object.optString("url", "")),
            object.optString("url", ""),
            object.optString("thumb", ""),
            object.optLong("visitedAt", 0L)));
      }
    } catch (JSONException ignored) {
      // Ignore corrupt payload.
    }
    // Trim any leftovers from a previous (larger) cap.
    while (recents.size() > MAX_ENTRIES) {
      RecentItem removed = recents.remove(recents.size() - 1);
      deleteFile(removed.getThumbnailPath());
    }
  }

  private void save() {
    JSONArray array = new JSONArray();
    for (RecentItem item : recents) {
      try {
        JSONObject object = new JSONObject();
        object.put("id", item.getId());
        object.put("title", item.getTitle());
        object.put("url", item.getUrl());
        object.put("thumb", item.getThumbnailPath());
        object.put("visitedAt", item.getVisitedAt());
        array.put(object);
      } catch (JSONException ignored) {
        // Skip.
      }
    }
    StorageManager.getInstance().putString(KEY_RECENTS, array.toString());
  }

  /**
   * Records a recent page. Re-opening any page of the same app/domain moves that
   * app's entry to the top and replaces its snapshot (so an app never appears
   * twice); the oldest entries beyond the 3-app cap are dropped and their
   * snapshot files deleted.
   *
   * @param title        page title
   * @param url          page URL
   * @param thumbnailPath absolute path to the captured snapshot (JPEG), or empty
   */
  public void add(String title, String url, String thumbnailPath) {
    if (url == null || url.isEmpty()) {
      return;
    }
    String domain = domainOf(url);
    Iterator<RecentItem> it = recents.iterator();
    while (it.hasNext()) {
      RecentItem existing = it.next();
      if (domainOf(existing.getUrl()).equals(domain)) {
        deleteFile(existing.getThumbnailPath());
        it.remove();
      }
    }
    recents.add(0, new RecentItem(
        UUID.randomUUID().toString(),
        title,
        url,
        thumbnailPath == null ? "" : thumbnailPath,
        System.currentTimeMillis()));
    while (recents.size() > MAX_ENTRIES) {
      RecentItem removed = recents.remove(recents.size() - 1);
      deleteFile(removed.getThumbnailPath());
    }
    save();
  }

  /**
   * Extracts the registrable domain (e.g. "bilibili.com" from
   * "https://www.bilibili.com/x/abc") so recents group by app rather than by
   * individual page URL.
   */
  private static String domainOf(String url) {
    if (url == null) {
      return "";
    }
    try {
      String host = new java.net.URI(url).getHost();
      if (host == null) {
        return url;
      }
      host = host.toLowerCase();
      if (host.startsWith("www.")) {
        host = host.substring(4);
      }
      String[] parts = host.split("\\.");
      if (parts.length >= 2) {
        return parts[parts.length - 2] + "." + parts[parts.length - 1];
      }
      return host;
    } catch (Exception ignored) {
      return url;
    }
  }

  /**
   * Returns a snapshot of the recents (newest first).
   *
   * @return copy of the recents list
   */
  public List<RecentItem> getRecents() {
    return new ArrayList<>(recents);
  }

  /**
   * Clears all recents and deletes their snapshot files.
   */
  public void clear() {
    for (RecentItem item : recents) {
      deleteFile(item.getThumbnailPath());
    }
    recents.clear();
    save();
  }

  private void deleteFile(String path) {
    if (path != null && !path.isEmpty()) {
      new File(path).delete();
    }
  }
}

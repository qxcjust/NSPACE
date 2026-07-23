package com.nspace.mediacenter.core;

import com.nspace.mediacenter.model.HistoryItem;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Tracks browsing history, persisted as a JSON array in MMKV.
 *
 * <p>Only the most recent {@link #MAX_ENTRIES} visits are retained.
 */
public final class HistoryManager {

  private static final HistoryManager INSTANCE = new HistoryManager();
  private static final String KEY_HISTORY = "nspace.history.v1";
  private static final int MAX_ENTRIES = 500;

  private final List<HistoryItem> history = new ArrayList<>();

  private HistoryManager() {
    load();
  }

  /**
   * Returns the singleton history manager.
   *
   * @return the shared instance
   */
  public static HistoryManager getInstance() {
    return INSTANCE;
  }

  private void load() {
    String raw = StorageManager.getInstance().getString(KEY_HISTORY, "[]");
    history.clear();
    try {
      JSONArray array = new JSONArray(raw);
      for (int i = 0; i < array.length(); i++) {
        JSONObject object = array.getJSONObject(i);
        history.add(new HistoryItem(
            object.getString("id"),
            object.optString("title", object.optString("url", "")),
            object.optString("url", ""),
            object.optLong("visitedAt", 0L)));
      }
    } catch (JSONException ignored) {
      // Ignore corrupt payload.
    }
  }

  private void save() {
    JSONArray array = new JSONArray();
    for (HistoryItem item : history) {
      try {
        JSONObject object = new JSONObject();
        object.put("id", item.getId());
        object.put("title", item.getTitle());
        object.put("url", item.getUrl());
        object.put("visitedAt", item.getVisitedAt());
        array.put(object);
      } catch (JSONException ignored) {
        // Skip.
      }
    }
    StorageManager.getInstance().putString(KEY_HISTORY, array.toString());
  }

  /**
   * Records a visit, de-duplicating consecutive repeats of the same URL.
   *
   * @param title page title
   * @param url   page URL
   */
  public void addVisit(String title, String url) {
    if (url == null || url.isEmpty()) {
      return;
    }
    if (!history.isEmpty() && history.get(0).getUrl().equals(url)) {
      history.get(0).setVisitedAt(System.currentTimeMillis());
    } else {
      history.add(0, new HistoryItem(UUID.randomUUID().toString(), title, url,
          System.currentTimeMillis()));
    }
    while (history.size() > MAX_ENTRIES) {
      history.remove(history.size() - 1);
    }
    save();
  }

  /**
   * Returns a snapshot of the history (newest first).
   *
   * @return copy of the history list
   */
  public List<HistoryItem> getHistory() {
    return new ArrayList<>(history);
  }

  /**
   * Clears all history.
   */
  public void clear() {
    history.clear();
    save();
  }
}

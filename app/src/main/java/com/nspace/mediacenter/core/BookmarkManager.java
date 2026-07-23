package com.nspace.mediacenter.core;

import com.nspace.mediacenter.model.Bookmark;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Manages the user's saved bookmarks, persisting them as a JSON array in MMKV.
 */
public final class BookmarkManager {

  private static final BookmarkManager INSTANCE = new BookmarkManager();
  private static final String KEY_BOOKMARKS = "nspace.bookmarks.v1";

  private final List<Bookmark> bookmarks = new ArrayList<>();

  private BookmarkManager() {
    load();
  }

  /**
   * Returns the singleton bookmark manager.
   *
   * @return the shared instance
   */
  public static BookmarkManager getInstance() {
    return INSTANCE;
  }

  private void load() {
    String raw = StorageManager.getInstance().getString(KEY_BOOKMARKS, "[]");
    bookmarks.clear();
    try {
      JSONArray array = new JSONArray(raw);
      for (int i = 0; i < array.length(); i++) {
        JSONObject object = array.getJSONObject(i);
        bookmarks.add(new Bookmark(
            object.getString("id"),
            object.optString("title", ""),
            object.optString("url", ""),
            object.optLong("createdAt", 0L)));
      }
    } catch (JSONException ignored) {
      // Corrupt payload: start fresh rather than crash.
    }
  }

  private void save() {
    JSONArray array = new JSONArray();
    for (Bookmark bookmark : bookmarks) {
      try {
        JSONObject object = new JSONObject();
        object.put("id", bookmark.getId());
        object.put("title", bookmark.getTitle());
        object.put("url", bookmark.getUrl());
        object.put("createdAt", bookmark.getCreatedAt());
        array.put(object);
      } catch (JSONException ignored) {
        // Skip unserialisable entry.
      }
    }
    StorageManager.getInstance().putString(KEY_BOOKMARKS, array.toString());
  }

  /**
   * Returns a snapshot copy of the current bookmarks (newest first).
   *
   * @return an immutable-style copy of the bookmark list
   */
  public List<Bookmark> getBookmarks() {
    return new ArrayList<>(bookmarks);
  }

  /**
   * Adds a bookmark to the top of the list.
   *
   * @param title display title
   * @param url   destination URL
   */
  public void addBookmark(String title, String url) {
    if (url == null || url.isEmpty()) {
      return;
    }
    bookmarks.add(0, new Bookmark(title, url, System.currentTimeMillis()));
    save();
  }

  /**
   * Removes the bookmark with the given identifier.
   *
   * @param id bookmark identifier
   */
  public void removeBookmark(String id) {
    bookmarks.removeIf(bookmark -> bookmark.getId().equals(id));
    save();
  }

  /**
   * Reports whether a URL is already bookmarked.
   *
   * @param url URL to test
   * @return true when a bookmark with the URL exists
   */
  public boolean isBookmarked(String url) {
    for (Bookmark bookmark : bookmarks) {
      if (bookmark.getUrl().equals(url)) {
        return true;
      }
    }
    return false;
  }
}

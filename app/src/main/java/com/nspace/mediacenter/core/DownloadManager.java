package com.nspace.mediacenter.core;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import com.nspace.mediacenter.model.DownloadItem;
import com.nspace.mediacenter.NspaceApplication;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Coordinates media/file downloads using the system {@link DownloadManager} and
 * keeps a persisted list of tracked downloads in MMKV.
 */
public final class DownloadManager {

  private static final DownloadManager INSTANCE = new DownloadManager();
  private static final String KEY_DOWNLOADS = "nspace.downloads.v1";

  private final List<DownloadItem> items = new ArrayList<>();
  private final Map<String, Long> idToSystemId = new ConcurrentHashMap<>();

  private DownloadManager() {
    load();
  }

  /**
   * Returns the singleton download manager.
   *
   * @return the shared instance
   */
  public static DownloadManager getInstance() {
    return INSTANCE;
  }

  private void load() {
    String raw = StorageManager.getInstance().getString(KEY_DOWNLOADS, "[]");
    items.clear();
    try {
      JSONArray array = new JSONArray(raw);
      for (int i = 0; i < array.length(); i++) {
        JSONObject object = array.getJSONObject(i);
        DownloadItem item = new DownloadItem(
            object.getString("id"),
            object.optString("url", ""),
            object.optString("fileName", "file"));
        item.setTotalBytes(object.optLong("totalBytes", -1L));
        item.setDownloadedBytes(object.optLong("downloadedBytes", 0L));
        item.setStatus(DownloadItem.Status.valueOf(object.optString("status", "PENDING")));
        items.add(item);
      }
    } catch (JSONException | IllegalArgumentException ignored) {
      // Ignore corrupt payload.
    }
  }

  private void save() {
    JSONArray array = new JSONArray();
    for (DownloadItem item : items) {
      try {
        JSONObject object = new JSONObject();
        object.put("id", item.getId());
        object.put("url", item.getUrl());
        object.put("fileName", item.getFileName());
        object.put("totalBytes", item.getTotalBytes());
        object.put("downloadedBytes", item.getDownloadedBytes());
        object.put("status", item.getStatus().name());
        array.put(object);
      } catch (JSONException ignored) {
        // Skip.
      }
    }
    StorageManager.getInstance().putString(KEY_DOWNLOADS, array.toString());
  }

  /**
   * Returns a snapshot of tracked downloads (newest first).
   *
   * @return copy of the download list
   */
  public List<DownloadItem> getDownloads() {
    return new ArrayList<>(items);
  }

  /**
   * Enqueues a download with the system download manager.
   *
   * @param url      source URL
   * @param fileName suggested file name
   * @return the created download item
   */
  public DownloadItem enqueue(String url, String fileName) {
    Context context = NspaceApplication.getInstance();
    DownloadItem item = new DownloadItem(UUID.randomUUID().toString(), url, fileName);
    item.setStatus(DownloadItem.Status.RUNNING);

    android.app.DownloadManager system =
        (android.app.DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
    if (system != null) {
      android.app.DownloadManager.Request request =
          new android.app.DownloadManager.Request(Uri.parse(url));
      request.setNotificationVisibility(
          android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
      request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
      long systemId = system.enqueue(request);
      idToSystemId.put(item.getId(), systemId);
    } else {
      item.setStatus(DownloadItem.Status.FAILED);
    }

    items.add(0, item);
    save();
    return item;
  }

  /**
   * Removes a tracked download from the list.
   *
   * @param id download identifier
   */
  public void remove(String id) {
    items.removeIf(item -> item.getId().equals(id));
    idToSystemId.remove(id);
    save();
  }

  /**
   * Clears completed/failed entries from the tracked list.
   */
  public void clearFinished() {
    items.removeIf(item -> item.getStatus() == DownloadItem.Status.COMPLETED
        || item.getStatus() == DownloadItem.Status.FAILED
        || item.getStatus() == DownloadItem.Status.CANCELLED);
    save();
  }
}

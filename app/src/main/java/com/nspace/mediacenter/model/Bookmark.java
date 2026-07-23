package com.nspace.mediacenter.model;

import java.io.Serializable;
import java.util.UUID;

/**
 * A user-saved bookmark for the built-in browser.
 *
 * <p>Bookmarks are persisted as JSON inside MMKV by {@code BookmarkManager}.
 */
public final class Bookmark implements Serializable {

  private final String id;
  private String title;
  private String url;
  private long createdAt;

  /**
   * Creates a new bookmark with a freshly generated identifier.
   *
   * @param title     display title
   * @param url       destination URL
   * @param createdAt creation timestamp in milliseconds
   */
  public Bookmark(String title, String url, long createdAt) {
    this(UUID.randomUUID().toString(), title, url, createdAt);
  }

  /**
   * Creates a bookmark with an explicit identifier (used when reconstructing from storage).
   *
   * @param id        stable identifier
   * @param title     display title
   * @param url       destination URL
   * @param createdAt creation timestamp in milliseconds
   */
  public Bookmark(String id, String title, String url, long createdAt) {
    this.id = id;
    this.title = title;
    this.url = url;
    this.createdAt = createdAt;
  }

  public String getId() {
    return id;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public long getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(long createdAt) {
    this.createdAt = createdAt;
  }
}

package com.nspace.mediacenter.model;

import java.io.Serializable;

/**
 * A single entry in the browsing history.
 */
public final class HistoryItem implements Serializable {

  private final String id;
  private String title;
  private String url;
  private long visitedAt;

  /**
   * Builds a history entry.
   *
   * @param id        stable identifier
   * @param title     page title
   * @param url       page URL
   * @param visitedAt visit timestamp in milliseconds
   */
  public HistoryItem(String id, String title, String url, long visitedAt) {
    this.id = id;
    this.title = title;
    this.url = url;
    this.visitedAt = visitedAt;
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

  public long getVisitedAt() {
    return visitedAt;
  }

  public void setVisitedAt(long visitedAt) {
    this.visitedAt = visitedAt;
  }
}

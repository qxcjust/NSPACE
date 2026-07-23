package com.nspace.mediacenter.model;

/**
 * A single "continue playing" entry: a recent page the user opened in the
 * in-app browser, captured together with a snapshot so it can be resumed from
 * the home screen.
 */
public final class RecentItem {

  private final String id;
  private final String title;
  private final String url;
  private final String thumbnailPath;
  private final long visitedAt;

  public RecentItem(String id, String title, String url, String thumbnailPath, long visitedAt) {
    this.id = id;
    this.title = title;
    this.url = url;
    this.thumbnailPath = thumbnailPath;
    this.visitedAt = visitedAt;
  }

  public String getId() {
    return id;
  }

  public String getTitle() {
    return title;
  }

  public String getUrl() {
    return url;
  }

  public String getThumbnailPath() {
    return thumbnailPath;
  }

  public long getVisitedAt() {
    return visitedAt;
  }
}

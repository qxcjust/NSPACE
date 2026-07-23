package com.nspace.mediacenter.model;

import java.io.Serializable;

/**
 * Represents a media/file download tracked by {@code DownloadManager}.
 */
public final class DownloadItem implements Serializable {

  /** Lifecycle states of a download. */
  public enum Status {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
  }

  private final String id;
  private String url;
  private String fileName;
  private long totalBytes;
  private long downloadedBytes;
  private Status status;

  /**
   * Builds a download item.
   *
   * @param id       stable identifier
   * @param url      source URL
   * @param fileName suggested file name
   */
  public DownloadItem(String id, String url, String fileName) {
    this.id = id;
    this.url = url;
    this.fileName = fileName;
    this.totalBytes = -1L;
    this.downloadedBytes = 0L;
    this.status = Status.PENDING;
  }

  public String getId() {
    return id;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getFileName() {
    return fileName;
  }

  public void setFileName(String fileName) {
    this.fileName = fileName;
  }

  public long getTotalBytes() {
    return totalBytes;
  }

  public void setTotalBytes(long totalBytes) {
    this.totalBytes = totalBytes;
  }

  public long getDownloadedBytes() {
    return downloadedBytes;
  }

  public void setDownloadedBytes(long downloadedBytes) {
    this.downloadedBytes = downloadedBytes;
  }

  public Status getStatus() {
    return status;
  }

  public void setStatus(Status status) {
    this.status = status;
  }

  /**
   * Returns download progress in the range [0, 1], or 0 when total is unknown.
   *
   * @return normalised progress
   */
  public float getProgress() {
    if (totalBytes <= 0) {
      return 0f;
    }
    return (float) downloadedBytes / (float) totalBytes;
  }
}

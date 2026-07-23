package com.nspace.mediacenter.core;

import android.webkit.CookieManager;
import android.webkit.WebStorage;

/**
 * Aggregates the privacy-cleanup operations exposed in the UI:
 * clearing history, cookies, and local web storage.
 */
public final class PrivacyManager {

  private static final PrivacyManager INSTANCE = new PrivacyManager();

  private PrivacyManager() {
  }

  /**
   * Returns the singleton privacy manager.
   *
   * @return the shared instance
   */
  public static PrivacyManager getInstance() {
    return INSTANCE;
  }

  /**
   * Removes all browsing history.
   */
  public void clearHistory() {
    HistoryManager.getInstance().clear();
  }

  /**
   * Removes all browser cookies.
   */
  public void clearCookies() {
    CookieManager cookieManager = CookieManager.getInstance();
    if (cookieManager != null) {
      cookieManager.removeAllCookies(null);
      cookieManager.flush();
    }
  }

  /**
   * Removes DOM storage / web databases for all origins.
   */
  public void clearWebStorage() {
    WebStorage storage = WebStorage.getInstance();
    if (storage != null) {
      storage.deleteAllData();
    }
  }

  /**
   * Performs a full privacy reset: history, cookies and web storage.
   */
  public void clearAll() {
    clearHistory();
    clearCookies();
    clearWebStorage();
  }
}

package com.nspace.mediacenter.ui;

/**
 * Navigation contract implemented by {@link MainActivity} and consumed by the
 * feature fragments, keeping the fragments decoupled from the host activity.
 */
public interface MainNavigator {

  /**
   * Opens a URL in the in-app browser.
   *
   * @param url absolute http(s) URL
   */
  void openUrl(String url);

  /**
   * Opens the home / search surface.
   */
  void goHome();

  /**
   * Shows the bookmarks surface.
   */
  void goBookmarks();

  /**
   * Shows the history surface.
   */
  void goHistory();

  /**
   * Shows the downloads surface.
   */
  void goDownloads();

  /**
   * Shows the privacy surface.
   */
  void goPrivacy();

  /**
   * Shows the settings surface.
   */
  void goSettings();

  /**
   * Shows the account surface.
   */
  void goAccount();
}

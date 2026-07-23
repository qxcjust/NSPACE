package com.nspace.mediacenter;

import android.app.Application;
import com.tencent.mmkv.MMKV;
import com.nspace.mediacenter.core.AccountManager;
import com.nspace.mediacenter.core.BookmarkManager;
import com.nspace.mediacenter.core.DownloadManager;
import com.nspace.mediacenter.core.HistoryManager;
import com.nspace.mediacenter.core.PrivacyManager;
import com.nspace.mediacenter.core.SearchEngine;

/**
 * Application entry point for NSpace.
 *
 * <p>Initialises MMKV (the persistent key-value store) and eagerly constructs the
 * singleton feature managers so their persisted state is loaded before the first
 * UI surface is shown.
 */
public final class NspaceApplication extends Application {

  private static NspaceApplication instance;

  @Override
  public void onCreate() {
    super.onCreate();
    instance = this;

    // MMKV must be initialised once, before any encode/decode call.
    MMKV.initialize(this);

    // Pre-load persisted state into the managers.
    BookmarkManager.getInstance();
    HistoryManager.getInstance();
    AccountManager.getInstance();
    DownloadManager.getInstance();
    PrivacyManager.getInstance();
    SearchEngine.getInstance();
  }

  /**
   * Returns the application instance.
   *
   * @return the singleton application instance
   */
  public static NspaceApplication getInstance() {
    return instance;
  }
}

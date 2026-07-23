package com.nspace.mediacenter.core;

import com.tencent.mmkv.MMKV;

/**
 * Thin wrapper around Tencent MMKV providing typed access to the default store.
 *
 * <p>All feature managers persist through this class so there is a single, testable
 * boundary to the underlying key-value engine.
 */
public final class StorageManager {

  private static final StorageManager INSTANCE = new StorageManager();
  private final MMKV mmkv;

  private StorageManager() {
    // defaultMMKV() returns the process-wide default instance created in
    // NspaceApplication.onCreate() via MMKV.initialize().
    mmkv = MMKV.defaultMMKV();
  }

  /**
   * Returns the singleton storage manager.
   *
   * @return the shared instance
   */
  public static StorageManager getInstance() {
    return INSTANCE;
  }

  public void putString(String key, String value) {
    mmkv.encode(key, value);
  }

  public String getString(String key, String defaultValue) {
    return mmkv.decodeString(key, defaultValue);
  }

  public void putBoolean(String key, boolean value) {
    mmkv.encode(key, value);
  }

  public boolean getBoolean(String key, boolean defaultValue) {
    return mmkv.decodeBool(key, defaultValue);
  }

  public void putLong(String key, long value) {
    mmkv.encode(key, value);
  }

  public long getLong(String key, long defaultValue) {
    return mmkv.decodeLong(key, defaultValue);
  }

  public void putInt(String key, int value) {
    mmkv.encode(key, value);
  }

  public int getInt(String key, int defaultValue) {
    return mmkv.decodeInt(key, defaultValue);
  }

  public void remove(String key) {
    mmkv.removeValueForKey(key);
  }

  public void clearAll() {
    mmkv.clearAll();
  }
}

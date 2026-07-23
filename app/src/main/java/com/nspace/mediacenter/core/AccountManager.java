package com.nspace.mediacenter.core;

import com.nspace.mediacenter.model.UserAccount;
import java.util.UUID;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Handles sign-in state for the local account.
 *
 * <p>This is a self-contained, privacy-first implementation: credentials are never
 * persisted. A non-identifying session token and display name are kept in MMKV so a
 * restart can restore the "signed-in" UI state without storing secrets.
 *
 * <p>In a real deployment the {@code signIn*} methods would call a backend; here they
 * emulate success so the UI flow is fully exercisable offline.
 */
public final class AccountManager {

  private static final AccountManager INSTANCE = new AccountManager();
  private static final String KEY_ACCOUNT = "nspace.account.v1";

  private UserAccount current;

  private AccountManager() {
    load();
  }

  /**
   * Returns the singleton account manager.
   *
   * @return the shared instance
   */
  public static AccountManager getInstance() {
    return INSTANCE;
  }

  private void load() {
    String raw = StorageManager.getInstance().getString(KEY_ACCOUNT, null);
    if (raw == null) {
      return;
    }
    try {
      JSONObject object = new JSONObject(raw);
      UserAccount account = new UserAccount();
      account.setUserId(object.optString("userId", ""));
      account.setDisplayName(object.optString("displayName", ""));
      account.setEmail(object.optString("email", ""));
      account.setSessionToken(object.optString("sessionToken", ""));
      account.setLoginAt(object.optLong("loginAt", 0L));
      String providerName = object.optString("provider", "EMAIL");
      account.setProvider(UserAccount.Provider.valueOf(providerName));
      current = account;
    } catch (JSONException | IllegalArgumentException ignored) {
      current = null;
    }
  }

  private void save() {
    if (current == null) {
      StorageManager.getInstance().remove(KEY_ACCOUNT);
      return;
    }
    try {
      JSONObject object = new JSONObject();
      object.put("userId", current.getUserId());
      object.put("displayName", current.getDisplayName());
      object.put("email", current.getEmail());
      object.put("sessionToken", current.getSessionToken());
      object.put("loginAt", current.getLoginAt());
      object.put("provider", current.getProvider().name());
      StorageManager.getInstance().putString(KEY_ACCOUNT, object.toString());
    } catch (JSONException ignored) {
      // Ignore.
    }
  }

  /**
   * Reports whether a user is currently signed in.
   *
   * @return true when a session exists
   */
  public boolean isSignedIn() {
    return current != null;
  }

  /**
   * Returns the active account, or null when signed out.
   *
   * @return the current account
   */
  public UserAccount getCurrent() {
    return current;
  }

  /**
   * Signs in with email and password (emulated).
   *
   * @param email    user email
   * @param password user password (validated locally only, never stored)
   * @return the resulting account
   */
  public UserAccount signInWithEmail(String email, String password) {
    UserAccount account = new UserAccount();
    account.setUserId(UUID.randomUUID().toString());
    account.setDisplayName(email.split("@")[0]);
    account.setEmail(email);
    account.setProvider(UserAccount.Provider.EMAIL);
    account.setSessionToken(UUID.randomUUID().toString());
    account.setLoginAt(System.currentTimeMillis());
    // Password is intentionally not retained.
    current = account;
    save();
    return account;
  }

  /**
   * Signs in via a third-party provider (emulated).
   *
   * @param provider the sign-in provider
   * @param token    opaque token returned by the provider
   * @return the resulting account
   */
  public UserAccount signInWithProvider(UserAccount.Provider provider, String token) {
    UserAccount account = new UserAccount();
    account.setUserId(UUID.randomUUID().toString());
    account.setDisplayName(provider.name().toLowerCase() + "-user");
    account.setProvider(provider);
    account.setSessionToken(token);
    account.setLoginAt(System.currentTimeMillis());
    current = account;
    save();
    return account;
  }

  /**
   * Signs the current user out and clears the persisted session.
   */
  public void signOut() {
    current = null;
    save();
  }
}

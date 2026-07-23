package com.nspace.mediacenter.model;

import java.io.Serializable;

/**
 * Minimal account model used by {@code AccountManager}.
 *
 * <p>This is a local, privacy-respecting representation. No credentials are persisted
 * in clear text; only a non-identifying session token and display name are kept.
 */
public final class UserAccount implements Serializable {

  /** Supported sign-in methods. */
  public enum Provider {
    EMAIL,
    GOOGLE,
    QR
  }

  private String userId;
  private String displayName;
  private String email;
  private Provider provider;
  private String sessionToken;
  private long loginAt;

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public Provider getProvider() {
    return provider;
  }

  public void setProvider(Provider provider) {
    this.provider = provider;
  }

  public String getSessionToken() {
    return sessionToken;
  }

  public void setSessionToken(String sessionToken) {
    this.sessionToken = sessionToken;
  }

  public long getLoginAt() {
    return loginAt;
  }

  public void setLoginAt(long loginAt) {
    this.loginAt = loginAt;
  }
}

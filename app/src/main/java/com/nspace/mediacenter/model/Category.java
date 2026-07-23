package com.nspace.mediacenter.model;

import java.io.Serializable;

/**
 * A top-level content category shown on the home screen
 * (for example games, music, news, video).
 */
public final class Category implements Serializable {

  private final String id;
  private final String title;
  private final String icon;

  /**
   * Builds a category descriptor.
   *
   * @param id    stable identifier
   * @param title display title
   * @param icon  drawable resource name or emoji glyph
   */
  public Category(String id, String title, String icon) {
    this.id = id;
    this.title = title;
    this.icon = icon;
  }

  public String getId() {
    return id;
  }

  public String getTitle() {
    return title;
  }

  public String getIcon() {
    return icon;
  }
}

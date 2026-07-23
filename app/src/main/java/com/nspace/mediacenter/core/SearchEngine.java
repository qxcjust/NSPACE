package com.nspace.mediacenter.core;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Describes a search provider and builds search URLs.
 *
 * <p>The default engine is stored as a preference in MMKV so the choice persists
 * across launches.
 */
public final class SearchEngine {

  private static final SearchEngine INSTANCE = new SearchEngine();
  private static final String KEY_ENGINE = "nspace.search.engine.v1";

  private final List<Engine> engines = new ArrayList<>();
  private Engine current;

  private SearchEngine() {
    engines.add(new Engine("google", "Google", "https://www.google.com/search?q=%s"));
    engines.add(new Engine("bing", "Bing", "https://www.bing.com/search?q=%s"));
    engines.add(new Engine("duckduckgo", "DuckDuckGo",
        "https://duckduckgo.com/?q=%s"));
    String saved = StorageManager.getInstance().getString(KEY_ENGINE, "google");
    current = find(saved);
  }

  /**
   * Returns the singleton search engine manager.
   *
   * @return the shared instance
   */
  public static SearchEngine getInstance() {
    return INSTANCE;
  }

  private Engine find(String id) {
    for (Engine engine : engines) {
      if (engine.id.equals(id)) {
        return engine;
      }
    }
    return engines.get(0);
  }

  /**
   * Lists the available search providers.
   *
   * @return the engine list
   */
  public List<Engine> getEngines() {
    return new ArrayList<>(engines);
  }

  /**
   * Returns the currently selected engine.
   *
   * @return the active engine
   */
  public Engine getCurrent() {
    return current;
  }

  /**
   * Selects an engine by id and persists the choice.
   *
   * @param id engine identifier
   */
  public void setCurrent(String id) {
    current = find(id);
    StorageManager.getInstance().putString(KEY_ENGINE, current.id);
  }

  /**
   * Builds a search URL for the given query using the active engine.
   *
   * @param query raw search query
   * @return a fully-formed http(s) URL
   */
  public String buildSearchUrl(String query) {
    String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
    return current.urlTemplate.replace("%s", encoded);
  }

  /**
   * Lightweight descriptor for a search provider.
   */
  public static final class Engine {
    private final String id;
    private final String name;
    private final String urlTemplate;

    /**
     * Builds an engine descriptor.
     *
     * @param id          stable identifier
     * @param name        display name
     * @param urlTemplate search URL with a single {@code %s} placeholder
     */
    public Engine(String id, String name, String urlTemplate) {
      this.id = id;
      this.name = name;
      this.urlTemplate = urlTemplate;
    }

    public String getId() {
      return id;
    }

    public String getName() {
      return name;
    }

    public String getUrlTemplate() {
      return urlTemplate;
    }
  }
}

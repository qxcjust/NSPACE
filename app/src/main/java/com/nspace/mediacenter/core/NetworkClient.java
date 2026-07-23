package com.nspace.mediacenter.core;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Minimal HTTP client used for fetching lightweight remote data (for example a
 * recommendations feed). Operations run on a shared background pool.
 */
public final class NetworkClient {

  private static final NetworkClient INSTANCE = new NetworkClient();
  private final ExecutorService executor = Executors.newFixedThreadPool(4);

  private NetworkClient() {
  }

  /**
   * Returns the singleton network client.
   *
   * @return the shared instance
   */
  public static NetworkClient getInstance() {
    return INSTANCE;
  }

  /**
   * Asynchronously fetches a URL body as a string.
   *
   * @param url target URL
   * @return a future resolving to the response body (or null on failure)
   */
  public Future<String> fetchAsync(final String url) {
    return executor.submit(new Callable<String>() {
      @Override
      public String call() {
        return fetch(url);
      }
    });
  }

  /**
   * Synchronously fetches a URL body as a string.
   *
   * @param target target URL
   * @return the response body, or null on any error
   */
  public String fetch(String target) {
    HttpURLConnection connection = null;
    try {
      URL url = new URL(target);
      connection = (HttpURLConnection) url.openConnection();
      connection.setRequestMethod("GET");
      connection.setConnectTimeout(10_000);
      connection.setReadTimeout(10_000);
      connection.connect();

      int code = connection.getResponseCode();
      if (code != HttpURLConnection.HTTP_OK) {
        return null;
      }
      InputStream stream = connection.getInputStream();
      BufferedReader reader = new BufferedReader(
          new InputStreamReader(stream, StandardCharsets.UTF_8));
      StringBuilder builder = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        builder.append(line);
      }
      return builder.toString();
    } catch (Exception ignored) {
      return null;
    } finally {
      if (connection != null) {
        connection.disconnect();
      }
    }
  }
}

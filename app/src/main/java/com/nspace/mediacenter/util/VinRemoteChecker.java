package com.nspace.mediacenter.util;

import android.os.Handler;
import android.os.Looper;
import com.nspace.mediacenter.BuildConfig;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Remote VIN revocation check.
 *
 * <p>On start the app reads the live car VIN, builds the per-VIN URL
 * {@code <VIN_REMOTE_URL><VIN>.enc}, fetches that encrypted file from GitHub
 * Pages, decrypts it locally with {@link BuildConfig#VIN_REMOTE_KEY}, and
 * compares the plaintext to the live VIN. One file per authorized vehicle.
 *
 * <ul>
 *   <li>{@code PASS}    – the remote file decrypted and matches the live VIN.</li>
 *   <li>{@code REVOKED} – the file is not published (HTTP 404); this VIN is not
 *                         on the remote allowlist, so it has been revoked.</li>
 *   <li>{@code OFFLINE} – network down / TLS error / timeout / decrypt failure;
 *                         trust the local binding (never lock the user out on a
 *                         transient error or because the car has no connectivity).</li>
 * </ul>
 *
 * <p>The check runs off the UI thread; the callback is invoked on the main
 * thread. It is fire-and-forget: the caller keeps the local binding as the
 * always-on gate and only escalates to a lock screen on a definitive
 * {@code REVOKED}.
 */
public final class VinRemoteChecker {

  public enum Result { PASS, REVOKED, OFFLINE }

  public interface Callback {
    void onResult(Result result);
  }

  // Wire format: [12-byte IV][ciphertext][16-byte GCM tag].
  private static final int IV_LEN = 12;
  private static final int TAG_LEN_BITS = 128;

  private VinRemoteChecker() {
  }

  /** Kick off the async check; result delivered on the main thread. */
  public static void verify(Callback cb) {
    new Thread(() -> {
      Result result = doCheck();
      new Handler(Looper.getMainLooper()).post(() -> cb.onResult(result));
    }).start();
  }

  private static Result doCheck() {
    String current = VinReader.getVin();
    if (current == null || current.isEmpty()) {
      // Can't even read the local VIN – fall back to the local binding gate
      // (which already ran and let us in). Don't lock remotely.
      return Result.OFFLINE;
    }
    byte[] data;
    try {
      data = download(BuildConfig.VIN_REMOTE_URL + current + ".enc");
    } catch (java.io.FileNotFoundException e) {
      // The per-VIN file is not published -> this vehicle is not on the
      // remote allowlist. Treat as a definitive revoke.
      return Result.REVOKED;
    } catch (Exception e) {
      // Network unavailable, TLS error, timeout -> trust local.
      return Result.OFFLINE;
    }
    if (data == null || data.length <= IV_LEN + (TAG_LEN_BITS / 8)) {
      return Result.OFFLINE;
    }
    try {
      byte[] iv = Arrays.copyOfRange(data, 0, IV_LEN);
      byte[] ctWithTag = Arrays.copyOfRange(data, IV_LEN, data.length);
      byte[] key = hexToBytes(BuildConfig.VIN_REMOTE_KEY);
      if (key == null || key.length != 32) {
        return Result.OFFLINE;
      }
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_LEN_BITS, iv));
      byte[] plain = cipher.doFinal(ctWithTag); // verifies the appended GCM tag
      String authorized = new String(plain, StandardCharsets.UTF_8).trim();
      if (current.equalsIgnoreCase(authorized)) {
        return Result.PASS;
      }
      // Published, decrypts, but the content doesn't match this VIN.
      return Result.REVOKED;
    } catch (Exception e) {
      // Corrupt file or wrong key -> don't lock on our own error; trust local.
      return Result.OFFLINE;
    }
  }

  private static byte[] download(String urlStr) throws Exception {
    URL url = new URL(urlStr);
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setConnectTimeout(4000);
    conn.setReadTimeout(4000);
    conn.setRequestMethod("GET");
    conn.setInstanceFollowRedirects(true);
    try {
      int code = conn.getResponseCode();
      if (code == HttpURLConnection.HTTP_NOT_FOUND) {
        throw new java.io.FileNotFoundException(urlStr);
      }
      if (code != HttpURLConnection.HTTP_OK) {
        return null;
      }
      try (InputStream in = conn.getInputStream()) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) {
          bos.write(buf, 0, n);
        }
        return bos.toByteArray();
      }
    } finally {
      conn.disconnect();
    }
  }

  private static byte[] hexToBytes(String hex) {
    if (hex == null || (hex.length() % 2) != 0) {
      return null;
    }
    byte[] out = new byte[hex.length() / 2];
    for (int i = 0; i < out.length; i++) {
      int v = Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
      out[i] = (byte) v;
    }
    return out;
  }
}

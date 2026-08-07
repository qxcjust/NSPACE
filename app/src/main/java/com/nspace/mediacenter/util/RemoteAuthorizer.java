package com.nspace.mediacenter.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.nspace.mediacenter.BuildConfig;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Remote authorization check backed by GitHub Pages.
 *
 * <p>For a given device id the app fetches {@code <AID_REMOTE_URL>/<id>.enc},
 * a per-device AES-256-GCM blob whose plaintext is the device id itself. The
 * file is published to authorize a device and deleted to revoke it. Decrypting
 * to this device's own id proves the file was minted for this exact device
 * (not a shared "authorized" blob that could be copied around).
 *
 * <p>Result semantics:
 * <ul>
 *   <li>{@code PASS}    – file fetched, decrypted, and matches this device id.</li>
 *   <li>{@code REVOKED} – 404 (not published) or any other non-200, or a 200
 *                         response that does not decrypt to this device id.</li>
 *   <li>{@code OFFLINE} – could not reach the endpoint (no network / DNS / TLS).</li>
 * </ul>
 *
 * <p>The network call runs on a background thread; the callback is posted to
 * the main thread.
 */
public final class RemoteAuthorizer {

  public enum Result { PASS, REVOKED, OFFLINE }

  private static final int TIMEOUT_MS = 8000;

  private RemoteAuthorizer() {
  }

  public interface Callback {
    void onResult(Result result);
  }

  public static void verify(Context ctx, Callback cb) {
    final String deviceId = DeviceId.getDeviceId(ctx);
    new Thread(() -> {
      final Result r = doVerify(deviceId);
      new Handler(Looper.getMainLooper()).post(() -> cb.onResult(r));
    }).start();
  }

  private static Result doVerify(String deviceId) {
    HttpURLConnection conn = null;
    try {
      String url = BuildConfig.AID_REMOTE_URL
          + URLEncoder.encode(deviceId, "UTF-8") + ".enc";
      conn = (HttpURLConnection) new URL(url).openConnection();
      conn.setConnectTimeout(TIMEOUT_MS);
      conn.setReadTimeout(TIMEOUT_MS);
      conn.setInstanceFollowRedirects(true);
      int code = conn.getResponseCode();
      if (code == 404) {
        return Result.REVOKED;
      }
      if (code != 200) {
        return Result.REVOKED;
      }
      byte[] body = readAll(conn.getInputStream());
      byte[] plain = decrypt(body);
      if (plain != null && new String(plain, "UTF-8").equals(deviceId)) {
        return Result.PASS;
      }
      return Result.REVOKED;
    } catch (IOException e) {
      // Unreachable endpoint -> cannot verify; let offline grace decide.
      return Result.OFFLINE;
    } catch (Exception e) {
      return Result.OFFLINE;
    } finally {
      if (conn != null) {
        conn.disconnect();
      }
    }
  }

  /** Decrypt an [IV(12)][ciphertext+GCM tag(16)] blob with the remote key. */
  private static byte[] decrypt(byte[] data) {
    try {
      if (data == null || data.length < 12 + 16) {
        return null;
      }
      byte[] iv = Arrays.copyOfRange(data, 0, 12);
      byte[] ctAndTag = Arrays.copyOfRange(data, 12, data.length);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE,
          new SecretKeySpec(KeyVault.remoteKey(), "AES"),
          new GCMParameterSpec(128, iv));
      return cipher.doFinal(ctAndTag); // verifies the tag, throws on mismatch
    } catch (Exception e) {
      return null;
    }
  }

  private static byte[] readAll(InputStream is) throws IOException {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    byte[] buf = new byte[4096];
    int n;
    while ((n = is.read(buf)) != -1) {
      bos.write(buf, 0, n);
    }
    return bos.toByteArray();
  }
}

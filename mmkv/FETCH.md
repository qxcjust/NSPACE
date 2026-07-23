# Fetching MMKV source (Tencent)

NSpace uses **Tencent MMKV** for local key-value storage. The App module depends on
the official Maven artifact `com.tencent:mmkv:1.3.11` by default, which is the compiled
output of the open-source source below.

If you want to **build MMKV from source** instead:

1. Clone the official Tencent MMKV source into this folder (`mmkv/`). The Android
   module lives under the repo's `Android/` directory and its C++ core under `Core/`.

   ```bash
   # From the NSpace project root:
   git clone --depth 1 https://gitee.com/mirrors/MMKV.git mmkv_tmp
   # The Android library module is at mmkv_tmp/Android; copy its contents here:
   cp -r mmkv_tmp/Android/. mmkv/
   rm -rf mmkv_tmp
   ```

   > Note: the Gitee `mirrors/MMKV` repository may require authentication. If the
   > clone is rejected, use the GitHub source instead:
   > `https://github.com/Tencent/MMKV.git` (clone, then copy `Android/` into `mmkv/`).

2. Enable the module in `settings.gradle`:
   ```gradle
   include(":app")
   include(":mmkv")
   ```

3. Switch the dependency in `app/build.gradle`:
   ```gradle
   // implementation 'com.tencent:mmkv:1.3.11'
   implementation project(':mmkv')
   ```

4. Sync and build. The NDK is required to compile the native layer.

## License

MMKV is released by Tencent under the **BSD 3-Clause License**. NSpace links to it as
an unmodified third-party dependency; no MMKV source is modified or redistributed here.

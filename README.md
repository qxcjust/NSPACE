# NSpace

A clean-room, from-scratch Android **TV media browser** built with **Java** (no Kotlin)
and the **Google Java Style Guide**. It re-implements the *architecture and feature set*
of a previously analysed APK, but contains **no original source, assets, strings, or
package names** from that product.

> **Declarations**
> - ✅ No occurrence of the original product name anywhere in the project.
> - ✅ De-identified / desensitised: generic branding ("NSpace"), neutral palette, no
>   proprietary logos, copy, or identifiers. This is an independent implementation,
>   not a copy or derivative of any specific vendor's code.
> - ✅ Java only; follows Google Java Style (2-space indent, camelCase fields, Javadoc
>   on public APIs).

## Architecture

Mirrors the analysed product's layered design:

```
┌─────────────────────────────────────────────────────────────┐
│  Android TV host (Leanback-capable, DPAD-friendly UI)         │
├─────────────────────────────────────────────────────────────┤
│  Native shell        NspaceApplication + MainActivity         │
│  Feature modules     ui/*  (Home, Browser, Bookmarks,         │
│                      History, Downloads, Privacy, Settings,   │
│                      Account)                                  │
│  Core (managers)    Storage / Bookmark / History / Account /  │
│                      Download / Privacy / Search / Network     │
│  Models             Bookmark / HistoryItem / DownloadItem /   │
│                      UserAccount / Category                    │
├─────────────────────────────────────────────────────────────┤
│  Storage layer       Tencent MMKV (com.tencent.mmkv)           │
│  UI toolkit          AndroidX + Material Components            │
│  Web engine          Android WebView                           │
└─────────────────────────────────────────────────────────────┘
```

Key differences from the analysed APK (clean-room choices):
- Native layer is **original Java**, not a decompiled/smali port.
- UI is **AndroidX + Material + NavigationView**, not the DCloud 5+ H5 engine.
- Storage is **MMKV** (the same high-performance KV engine the original used), linked
  as an unmodified third-party dependency.

## Module layout

```
NSpace/
├── settings.gradle
├── build.gradle
├── gradle.properties
├── gradle/wrapper/
├── app/
│   ├── build.gradle
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/nspace/mediacenter/
│       │   ├── NspaceApplication.java
│       │   ├── core/      # StorageManager, BookmarkManager, HistoryManager,
│       │   │              # AccountManager, DownloadManager, PrivacyManager,
│       │   │              # SearchEngine, NetworkClient
│       │   ├── model/     # Bookmark, HistoryItem, DownloadItem, UserAccount, Category
│       │   └── ui/        # MainActivity + 8 feature fragments + MainNavigator
│       └── res/           # values, layout, menu, drawable (vector launcher)
└── mmkv/                 # optional :mmkv source module (see mmkv/FETCH.md)
```

## Features

| Feature            | Status | Notes                                         |
|--------------------|--------|-----------------------------------------------|
| In-app WebView     | ✅     | Address bar, history capture, back nav       |
| Home / search      | ✅     | Multi-engine search + category grid          |
| Bookmarks          | ✅     | Add / open / remove, persisted in MMKV       |
| History            | ✅     | Auto-capture, capped at 500, clearable       |
| Downloads          | ✅     | System DownloadManager, tracked list         |
| Privacy cleanup    | ✅     | Clear history / cookies / web storage / all  |
| Account            | ✅     | Email / Google / QR sign-in (local, no creds)|
| Settings           | ✅     | Default search engine, version info          |

## Building

Requirements: Android SDK (compileSdk 34), JDK 17, Gradle 8.2 (wrapper provided).

```bash
# Generate the wrapper jar if needed, then build:
gradle wrapper --gradle-version 8.2
./gradlew assembleDebug
```

The app depends on `com.tencent:mmkv:1.3.11` (official Maven artifact) by default.
To build MMKV **from source** instead, follow `mmkv/FETCH.md`.

## MMKV — source from Gitee (per request)

The source for MMKV is fetched from Gitee per the project requirement. In this
environment the public Gitee mirror (`mirrors/MMKV`) required authentication, so the
exact clone command is documented in `mmkv/FETCH.md`; run it on a machine with access
(or substitute the GitHub source) and flip the dependency as described there. The
default Maven dependency already provides the identical, officially-built engine.

## License & compliance

- NSpace itself is original code released for internal/learning use.
- MMKV © Tencent, **BSD 3-Clause**. Used unmodified as a dependency.
- This project does not include, reproduce, or redistribute any third-party
  proprietary assets, trademarks, or source.

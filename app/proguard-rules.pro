# NSpace proguard rules.
# MMKV uses JNI and reflection; keep its public API intact.
-keep class com.tencent.mmkv.** { *; }
-dontwarn com.tencent.mmkv.**

# Keep model classes used with Serializable across process/state restore.
-keep class com.nspace.mediacenter.model.** { *; }

# WebView / JavascriptInterface retained for the in-app browser.
-keepattributes *Annotation*,Signature

# ---------------------------------------------------------------------------
# Anti-decompile hardening
# ---------------------------------------------------------------------------

# Rename everything we are allowed to (classes/methods/fields) into short
# identifiers, and collapse the package tree into a single flat package so the
# original package structure is not visible in a decompiler.
-allowaccessmodification
-repackageclasses 'o'
-optimizationpasses 5

# Strip all Log calls from the release binary so decompiled code reveals no
# debug strings or branch hints.
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# Keep the Application subclass and the launched Activity (referenced by the
# manifest, so R8 already preserves them, but be explicit).
-keep public class com.nspace.mediacenter.NspaceApplication
-keep public class com.nspace.mediacenter.ui.MainActivity

# Keep line numbers minimal; do not keep SourceFile so stack traces expose
# only obfuscated names (reduces information leakage).
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable

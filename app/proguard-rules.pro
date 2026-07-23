# NSpace proguard rules.
# MMKV uses JNI and reflection; keep its public API intact.
-keep class com.tencent.mmkv.** { *; }
-dontwarn com.tencent.mmkv.**

# Keep model classes used with Serializable across process/state restore.
-keep class com.nspace.mediacenter.model.** { *; }

# WebView / JavascriptInterface retained for the in-app browser.
-keepattributes *Annotation*,Signature

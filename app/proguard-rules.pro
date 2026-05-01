# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# =========================
# Sardine-Android 混淆规则
# =========================
-keep class com.thegrizzlylabs.sardineandroid.** { *; }

# Sardine 依赖 SimpleXML，需要保留
-keep class org.simpleframework.xml.** { *; }
-keepclassmembers class * {
    @org.simpleframework.xml.* *;
}

# =========================
# OkHttp 3 混淆规则
# =========================
-keepattributes Signature
-keepattributes *Annotation*
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# =========================
# Glide 混淆规则
# =========================
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep public class * extends com.bumptech.glide.module.LibraryGlideModule
-keep class com.bumptech.glide.GeneratedAppGlideModuleImpl
-keep class com.bumptech.glide.load.resource.bitmap.VideoDecoder {
  public <methods>;
}

# 针对 Glide 使用的反射
-keepnames class * {
  @com.bumptech.glide.annotation.GlideModule <methods>;
  @com.bumptech.glide.annotation.GlideOption <methods>;
  @com.bumptech.glide.annotation.GlideType <methods>;
}
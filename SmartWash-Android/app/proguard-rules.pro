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
# Retrofit接口不混淆
-keep interface retrofit2.** { *; }
-keep class retrofit2.** { *; }
-dontwarn retrofit2.**

# Gson - 保留字段名不被混淆
-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**
# 保留数据类字段
-keep class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keepattributes Signature
-keepattributes *Annotation*

# 保留标记为 Keep 的类
-keep class ** {
    @androidx.annotation.Keep *;
}

# OkHttp
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**

# Hilt / Dagger
-keep class dagger.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.EntryPoint
-dontwarn dagger.**
-dontwarn javax.inject.**

# Compose 运行时保留
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Room
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# 网络层数据类（Gson 反序列化依赖字段名，混淆后需保留）
-keep class com.smartwash.network.vo.** { *; }
-keep class com.smartwash.network.entity.** { *; }
-keep class com.smartwash.database.entity.** { *; }

# Kotlin 元数据（反射/Hilt 需要）
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

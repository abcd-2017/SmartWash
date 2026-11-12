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

# 观象台排盘 Gson 模型（DivChartCodec 序列化 Room chartJson）：
# 混淆会抹掉字段名与字段泛型 Signature（rows: List<LiuYaoLine> 退化为裸 ArrayList），
# Gson 反序列化把 rows 变成 List<LinkedTreeMap>，卦盘页渲染时 ClassCastException 闪退；
# 同时字段名混淆会让存量 chartJson 在版本升级后键名对不上，故整包保留（含未来新增模型）
-keep class com.smartwash.feature.divination.core.** { *; }
-keep class com.smartwash.feature.divination.data.** { *; }

# OkHttp
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**

# Hilt / Dagger
-keep class dagger.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.EntryPoint
-dontwarn dagger.**
-dontwarn javax.inject.**

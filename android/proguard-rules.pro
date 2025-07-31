# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
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

# Keep Kotlin metadata
-keep class kotlin.Metadata { *; }

# Keep Jetty classes
-keep class org.eclipse.jetty.** { *; }
-dontwarn org.eclipse.jetty.**

# Keep Jackson classes
-keep class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.**

# Keep SLF4J classes
-keep class org.slf4j.** { *; }
-dontwarn org.slf4j.**

# Keep application classes
-keep class com.simiacryptus.cognotik.** { *; }

# Keep AWS SDK classes
-keep class software.amazon.awssdk.** { *; }
-dontwarn software.amazon.awssdk.**
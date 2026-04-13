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
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
-renamesourcefileattribute SourceFile

# Remove logging in release builds
#-assumenosideeffects class android.util.Log {
#    public static boolean isLoggable(java.lang.String, int);
#    public static int v(...);
#    public static int i(...);
#    public static int w(...);
#    public static int d(...);
#    public static int e(...);
#    public static java.lang.String getStackTraceString(java.lang.Throwable);
#}

# PDFBox-Android rules
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-dontwarn org.apache.pdfbox.**
-dontwarn org.bouncycastle.**
-dontwarn com.gemalto.jp2.**
-dontwarn org.apache.harmony.**
-dontwarn javax.xml.stream.**
-dontwarn sun.misc.Unsafe

# Bouncy Castle rules (often used by PDFBox)
-keep class org.bouncycastle.jcajce.provider.keystore.bc.BcKeyStoreSpi { *; }
-keep class org.bouncycastle.jcajce.provider.keystore.PKCS12$Mappings { *; }
-keep class org.bouncycastle.jce.provider.BouncyCastleProvider { *; }
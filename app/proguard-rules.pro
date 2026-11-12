# CarLog v2 release shrinking rules (used with proguard-android-optimize.txt).

# ---- Pleos Playground SDKs (Vehicle, FusedLocation, SpeechToText, TextToSpeech) ----
# They bind to car services over AIDL, look up permissions/annotations at runtime and (de)serialise with Gson,
# so keep every class and member as shipped.
-keep class ai.pleos.** { *; }
-keep interface ai.pleos.** { *; }
-keep enum ai.pleos.** { *; }
-dontwarn ai.pleos.**

# Our implementations of SDK listeners/callbacks (anonymous objects included) must keep their method names.
-keep class * implements ai.pleos.** { *; }
-keep class * extends ai.pleos.** { *; }

# AIDL stubs/proxies and parcelables used across the binder boundary.
-keep class * implements android.os.IInterface { *; }
-keep class * extends android.os.Binder { *; }
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Kotlin metadata / reflection (FusedLocation depends on kotlin-reflect) and generic signatures for Gson.
-keep class kotlin.Metadata { *; }
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,Exceptions
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-dontwarn com.google.gson.**
-dontwarn kotlin.reflect.jvm.internal.**
-dontwarn javax.annotation.**
-dontwarn com.google.errorprone.annotations.**

# ---- CarLog ----
# Platform callback contracts (VehicleFeed, VoiceInput.Callback, Speaker) stay readable in stack traces and
# safe for any reflective/listener use.
-keep interface com.carlog.app.platform.** { *; }
# Enum names are persisted in the database and snapshot JSON (Category, Priority, DataMode, LinkState, FieldStatus).
-keepclassmembers enum com.carlog.app.domain.** {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Readable crash reports from the vehicle (line numbers only; file names hidden).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

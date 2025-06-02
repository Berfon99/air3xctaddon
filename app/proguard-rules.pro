# Preserve org.json classes
-keep class org.json.** { *; }
-keep interface org.json.** { *; }

# Preserve Gson and generic signatures
-keep class com.google.gson.** { *; }
-keep interface com.google.gson.** { *; }
-keepattributes Signature,RuntimeVisibleAnnotations,AnnotationDefault,*Annotation*

# Critical: Preserve generic signatures for Gson TypeToken
-keepattributes Signature
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# Keep generic signatures for all classes
-keepattributes Signature,InnerClasses,EnclosingMethod

# Preserve TelegramChat and Task
-keep class com.xc.air3xctaddon.TelegramChat { *; }
-keep class com.xc.air3xctaddon.Task { *; }
-keep class com.xc.air3xctaddon.TelegramBotInfo { *; }
-keepclassmembers class com.xc.air3xctaddon.TelegramChat,com.xc.air3xctaddon.Task,com.xc.air3xctaddon.TelegramBotInfo {
    *;
}

# Preserve TelegramBotHelper and its methods
-keep class com.xc.air3xctaddon.TelegramBotHelper { *; }
-keepclassmembers class com.xc.air3xctaddon.TelegramBotHelper {
    public *;
    private *;
}

# Preserve SettingsRepository
-keep class com.xc.air3xctaddon.SettingsRepository { *; }
-keepclassmembers class com.xc.air3xctaddon.SettingsRepository {
    public *;
}

# Preserve OkHttp
-dontwarn okio.**
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Preserve Room
-keep class androidx.room.** { *; }
-keep interface androidx.room.** { *; }

# Preserve DataStore
-keep class androidx.datastore.** { *; }
-keepclassmembers class androidx.datastore.** { *; }

# Keep all model classes that might be serialized/deserialized
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep classes that use reflection (common in JSON parsing)
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# Keep line numbers for debugging
-keepattributes SourceFile,LineNumberTable

# Keep callback interfaces and lambda expressions
-keep class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Prevent obfuscation of callback methods
-keepclassmembers class * {
    public void onResult(...);
    public void onError(...);
}
# --- General Android & Compose ---
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-dontwarn androidx.compose.**

# --- Firebase ---
# Firebase SDKs bundle their own rules. Suppression of harmless warnings.
-dontwarn com.google.firebase.**

# --- OkHttp & Okio ---
# OkHttp bundles its own rules. Specific keeps for reflection-based features.
-keepattributes Signature, *Annotation*
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# --- Gson ---
# Targeting specific Gson components instead of keeping the whole library
-keep class com.google.gson.annotations.SerializedName { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class com.google.gson.stream.JsonReader { *; }
-keep class com.google.gson.stream.JsonWriter { *; }

# --- Coil ---
# Coil bundles its own rules.
-dontwarn coil.**

# --- Google Identity & Credentials ---
# These libraries bundle their own rules.
-dontwarn com.google.android.libraries.identity.googleid.**

# --- App Specific ---
# Keep essential entry points and data models used in reflection
-keep class com.lgcinovations.laughcoin.MainActivity { *; }
-keep class com.lgcinovations.laughcoin.EmojiState { *; }

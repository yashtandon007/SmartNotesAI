# kotlinx.serialization: keep generated serializers for the Gemini DTOs
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class com.yashtandon.smartnotes.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.yashtandon.smartnotes.**$$serializer { *; }

# Retrofit service interfaces use reflection on generic signatures
-keepattributes Signature, Exceptions
-keep,allowobfuscation interface com.yashtandon.smartnotes.data.remote.GeminiApi

# SQLCipher loads classes via JNI
-keep class net.sqlcipher.** { *; }
-dontwarn net.sqlcipher.**

# Desktop-JVM reflection types referenced by the Anthropic SDK's transitive
# jsonschema-generator (tool-runner path, unused on Android)
-dontwarn java.lang.reflect.AnnotatedParameterizedType
-dontwarn java.lang.reflect.AnnotatedType

# Anthropic SDK / OkHttp / kotlinx-serialization
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.slf4j.**
-keep class com.anthropic.** { *; }
-keepclassmembers class kotlinx.serialization.json.** { *; }

# Proguard rules for Z.AI Chat
# kotlinx.serialization: DTOs + enums + generated serializers
-keepattributes *Annotation*, InnerClasses, Signature
-keep class com.zai.chat.network.model.** { *; }
-keep class com.zai.chat.data.model.** { *; }
-keep,includedescriptorclasses class com.zai.chat.**$$serializer { *; }
-keepclassmembers class com.zai.chat.** { *** Companion; }
-keepclassmembers enum com.zai.chat.** { values(); valueOf(); }

# Dependencies compile-only annotations (Tink / OkHttp / CheckerFramework)
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**

# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keep class com.bunny.BunnyApplication { *; }
-keep class dagger.** { *; }
-keep class javax.inject.** { *; }
-keep class com.google.gson.** { *; }
-keep class com.bunny.domain.model.** { *; }
-keep class com.bunny.data.remote.dto.** { *; }
-keep class com.bunny.data.local.entity.** { *; }
-keep class androidx.room.** { *; }
-keep @androidx.room.Entity class * { *; }

-dontwarn com.bunny.**

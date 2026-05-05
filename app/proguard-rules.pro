# Keep BillingClient response classes (referenced by reflection internally)
-keep class com.android.billingclient.api.** { *; }

# Room: keep generated DAOs / database / entities
-keep class jp.simplist.memo.data.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }

# Kotlin
-keep class kotlin.Metadata { *; }
-dontwarn kotlinx.coroutines.**

# ---------------------------------------------------------------------------
# Splitlee R8 / ProGuard keep-rules for the release build.
# Philosophy: shrink + optimize the libraries (where most size and cost is)
# while keeping anything used reflectively or by-name so nothing breaks at
# runtime. There is no crash reporting in this app, so we also keep readable
# stack traces.
# ---------------------------------------------------------------------------

# Readable crash stack traces (file + line), source name hidden.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- App's own classes -----------------------------------------------------
# Kept whole, on purpose. The app stores enum names and structured records in
# Room and reconstructs them by name (e.g. PendingPaymentStatus.valueOf), and
# a solo project has no crash reporting to catch a subtle R8-stripped field.
# Libraries below are still fully shrunk/optimized, which is what makes the
# release build smooth. Drop this line later if you want to obfuscate app code.
-keep class com.example.splitwiseauto.** { *; }

# --- WorkManager -----------------------------------------------------------
# Workers are instantiated by class name via reflection — must keep their ctor.
-keep public class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# --- Room ------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keepclassmembers class * { @androidx.room.* <methods>; }
-dontwarn androidx.room.paging.**

# --- Enums (reconstructed by name from the DB) -----------------------------
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# --- ZXing (QR decoding) ---------------------------------------------------
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# --- Google Maps / Play Services -------------------------------------------
-keep class com.google.android.gms.maps.** { *; }
-keep interface com.google.android.gms.maps.** { *; }
-dontwarn com.google.android.gms.**

# --- Kotlin / Coroutines (consumer rules usually cover these; belt & braces)
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlin.Metadata { *; }

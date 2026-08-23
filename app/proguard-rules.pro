# ============================================================
#  Z-Mastery ProGuard / R8 Rules
# ============================================================

# ─── Kotlin Serialization ───
# Keep @Serializable classes and their companions
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep @Serializable classes
-keep,includedescriptorclasses class com.zmastery.english.data.**$$serializer { *; }
-keepclassmembers class com.zmastery.english.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.zmastery.english.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ─── App data classes (serialized to/from JSON) ───
-keep class com.zmastery.english.data.AppState { *; }
-keep class com.zmastery.english.data.*Dto { *; }
-keep class com.zmastery.english.data.CoursePackage { *; }
-keep class com.zmastery.english.data.LessonPackage { *; }
-keep class com.zmastery.english.data.JsonLesson { *; }
-keep class com.zmastery.english.data.JsonWord { *; }
-keep class com.zmastery.english.data.JsonDialogue { *; }
-keep class com.zmastery.english.data.JsonQuiz { *; }
-keep class com.zmastery.english.data.LessonMeta { *; }
-keep class com.zmastery.english.data.LessonContent { *; }
-keep class com.zmastery.english.data.BackupManager$FullBackup { *; }
-keep class com.zmastery.english.data.BackupManager$LessonsBackup { *; }
-keep class com.zmastery.english.data.BackupManager$WordsBackup { *; }
-keep class com.zmastery.english.data.MirrorReport { *; }
-keep class com.zmastery.english.data.RescueMission { *; }
-keep class com.zmastery.english.data.MysteryReward { *; }
-keep class com.zmastery.english.data.DayStat { *; }
-keep class com.zmastery.english.data.ReviewSignalDto { *; }
-keep class com.zmastery.english.data.ChestRecord { *; }
-keep class com.zmastery.english.data.PerkWallet { *; }
-keep class com.zmastery.english.data.CoachReport { *; }
-keep class com.zmastery.english.data.StudyPlanDto { *; }
-keep class com.zmastery.english.data.StoryDto { *; }

# ─── Widget (RemoteViews — accessed by launcher process) ───
-keep class com.zmastery.english.widget.ZMasteryWidget { *; }
-keep class com.zmastery.english.widget.WidgetBootReceiver { *; }

# ─── Firebase ───
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# ─── OkHttp / Coil ───
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn coil3.**

# ─── Compose ───
-dontwarn androidx.compose.**

# ─── Enum classes (used by serialization) ───
-keepclassmembers enum com.zmastery.english.data.** {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ─── Keep line numbers for crash reports ───
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

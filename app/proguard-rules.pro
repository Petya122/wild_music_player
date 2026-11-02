## Rules for NewPipeExtractor
# The extractor and its JavaScript engine (Rhino, used to decipher YouTube stream URLs)
# rely on reflection, so keep them whole.
-keep class org.schabi.newpipe.extractor.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.tools.**
-dontwarn javax.script.**
-dontwarn java.beans.**
# Rhino's invokedynamic optimizer only works on desktop JVMs; Android runs Rhino interpreted
-dontwarn jdk.dynalink.**

## Rules for WorkManager (background loudness measuring)
# WorkManager's Room database is created by class name at app start (before any app code);
# R8 renaming it made every launch crash in v0.8.0 ("Failed to create an instance of
# androidx.work.impl.WorkDatabase"). Keep Room databases and their generated _Impl classes.
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class androidx.work.impl.WorkDatabase_Impl { *; }
# Workers and input mergers are also created by class name when a job runs
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.InputMerger { public <init>(); }

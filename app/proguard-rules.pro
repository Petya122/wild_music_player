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

# Matrix Rust SDK / JNI
-keep class org.matrix.** { *; }
-keep class uniffi.** { *; }

# JNA reads Pointer.peer and other fields from native code. Shrinking or
# renaming them fails client startup with "Can't obtain peer field ID".
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-keepclassmembers class com.sun.jna.Pointer { long peer; }
-dontwarn com.sun.jna.**
-dontwarn java.awt.**

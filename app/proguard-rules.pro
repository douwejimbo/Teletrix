# Keep Matrix SDK + coroutines for release minification
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keep class * extends androidx.work.Worker
-dontwarn okhttp3.**

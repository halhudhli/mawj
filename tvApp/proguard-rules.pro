# libmpv calls back into these classes from native code.
-keep class is.xyz.mpv.** { *; }
-dontwarn org.slf4j.**
-dontwarn io.ktor.**
-dontwarn kotlinx.serialization.**

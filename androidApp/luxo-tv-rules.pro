# LUXO TV build (R8). Keep the player exactly as it is (native decoders, JNI callbacks).
-keep class com.nuvio.app.features.player.** { *; }
-keepclassmembers class * { native <methods>; }
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,Exceptions
# Debug variant pulls a few optional classes R8 can't see; they are never loaded on a TV.
-ignorewarnings
-dontwarn **

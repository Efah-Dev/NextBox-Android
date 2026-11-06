# gomobile bindings are looked up by name from native code.
-keep class go.** { *; }
-keep class dev.efah.nextbox.libbox.** { *; }
-keep class dev.efah.nextbox.nbcore.** { *; }
# Kotlin objects implementing gomobile interfaces are called through JNI.
-keep class * implements dev.efah.nextbox.libbox.PlatformInterface { *; }
-keep class * implements dev.efah.nextbox.libbox.CommandServerHandler { *; }
-keep class * implements dev.efah.nextbox.libbox.CommandClientHandler { *; }
-keep class * implements dev.efah.nextbox.libbox.LocalDNSTransport { *; }
-keep class * implements dev.efah.nextbox.libbox.StringIterator { *; }
-keep class * implements dev.efah.nextbox.libbox.NetworkInterfaceIterator { *; }
-keep class * implements dev.efah.nextbox.nbcore.LatencyHandler { *; }

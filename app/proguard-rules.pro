# JNI entry points from the pinned Termux PTY implementation.
-keep class com.termux.terminal.JNI { *; }
# termlib loads JNI by class/method name. Retain the native bridge explicitly.
-keepclasseswithmembernames,includedescriptorclasses class * { native <methods>; }

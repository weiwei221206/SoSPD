# Upstream instantiates item classes and restores bundles through reflection.
-keepnames class com.shatteredpixel.shatteredpixeldungeon.**
-keepclassmembers class com.shatteredpixel.shatteredpixeldungeon.** { public <init>(); }
-keepclassmembers class * implements com.watabou.utils.Bundlable { *; }
-keep class io.github.sospd.domain.Models$* { *; }
-keepattributes Signature,RuntimeVisibleAnnotations,AnnotationDefault,Record


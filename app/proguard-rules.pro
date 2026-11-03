# Upstream instantiates item classes and restores bundles through reflection.
-keepnames class com.shatteredpixel.shatteredpixeldungeon.**
-keepclassmembers class com.shatteredpixel.shatteredpixeldungeon.** { public <init>(); }
-keepclassmembers class * implements com.watabou.utils.Bundlable { *; }
-keep class io.github.sospd.domain.Models$* { *; }
-keepattributes Signature,RuntimeVisibleAnnotations,AnnotationDefault,Record
-keepclassmembers class com.shatteredpixel.shatteredpixeldungeon.journal.Document {
    java.util.LinkedHashMap pagesStates;
}
# The headless bridge dispatches these Java Proxy calls by their interface method names.
-keep interface com.badlogic.gdx.Application { *; }
-keep interface com.badlogic.gdx.Files { *; }
-keep interface com.badlogic.gdx.Input { *; }

# ML Kit discovers these through manifest metadata and calls the no-arg constructors reflectively.
# Firebase's older consumer rule keeps class names but R8 full mode can still remove the constructors.
-keepclassmembers class * implements com.google.firebase.components.ComponentRegistrar {
    public <init>();
}


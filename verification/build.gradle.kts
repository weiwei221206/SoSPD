import java.net.URI
import java.security.MessageDigest

plugins { java }
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
val upstream = rootProject.layout.projectDirectory.dir(".local/reference-game/v4.0.2")
val official = sourceSets.create("official") {
    java.srcDirs(upstream.dir("core/src/main/java"), upstream.dir("SPD-classes/src/main/java"),
        upstream.dir("services/src/main/java"), "src/shared/java", "src/official/java", layout.buildDirectory.dir("bridge"))
    resources.srcDir(upstream.dir("core/src/main/assets"))
    resources.include("messages/**/*.properties")
}
val toolkit = sourceSets.create("toolkit") {
    java.srcDirs("src/shared/java", "src/toolkit/java")
}
dependencies {
    add(official.implementationConfigurationName, project(":domain"))
    add(official.implementationConfigurationName, "com.badlogicgames.gdx:gdx:1.14.0")
    add(official.implementationConfigurationName, "com.badlogicgames.gdx:gdx-freetype:1.14.0")
    add(official.implementationConfigurationName, "com.badlogicgames.gdx-controllers:gdx-controllers-core:2.2.4")
    add(official.implementationConfigurationName, "org.json:json:20170516")
    add(toolkit.implementationConfigurationName, project(":engine"))
    add(toolkit.implementationConfigurationName, "com.badlogicgames.gdx:gdx:1.14.0")
}
tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }

val prepareOfficial by tasks.registering {
    val archive = layout.buildDirectory.file("official-v4.0.2.zip")
    outputs.dir(upstream)
    doLast {
        if (!upstream.file("core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/Dungeon.java").asFile.exists()) {
            archive.get().asFile.parentFile.mkdirs()
            URI("https://codeload.github.com/00-Evan/shattered-pixel-dungeon/zip/57a4e06a4caf162446d1c28caa7983f0493fecf0")
                .toURL().openStream().use { input -> archive.get().asFile.outputStream().use { input.copyTo(it) } }
            copy {
                from(zipTree(archive))
                into(upstream)
                eachFile { relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray()) }
                includeEmptyDirs = false
            }
        }
        // The official oracle gets rendering-only substitutions, never generation changes.
        val sprite = upstream.file("core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/sprites/ItemSpriteSheet.java").asFile
        val source = sprite.readText()
            .replace("new TextureFilm( Assets.Sprites.ITEMS, SIZE, SIZE )", "new TextureFilm( 256, 512, SIZE, SIZE )")
            .replace("new TextureFilm( Assets.Sprites.ITEM_ICONS, SIZE, SIZE )", "new TextureFilm( 128, 64, SIZE, SIZE )")
        sprite.writeText(source)
        val level = upstream.file("core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/levels/Level.java").asFile
        level.writeText(level.readText().replace("\r\n", "\n").replace(
            "ItemSprite sprite = heap.sprite = new ItemSprite();\n\t\t\tsprite.link(heap);",
            "if (ShatteredPixelDungeon.scene() != null) {\n\t\t\t\tItemSprite sprite = heap.sprite = new ItemSprite();\n\t\t\t\tsprite.link(heap);\n\t\t\t}"
        ))
    }
}
val copyBridge by tasks.registering(Copy::class) {
    from(rootProject.file("engine/src/main/java/io/github/sospd/engine/HeadlessRuntime.java"))
    into(layout.buildDirectory.dir("bridge/io/github/sospd/engine"))
}
tasks.named(official.compileJavaTaskName) { dependsOn(prepareOfficial, copyBridge) }
tasks.named(official.processResourcesTaskName) { dependsOn(prepareOfficial) }

val officialProbe by tasks.registering(JavaExec::class) {
    dependsOn(official.classesTaskName)
    classpath = official.runtimeClasspath
    mainClass = "io.github.sospd.engine.OfficialProbe"
    args(layout.buildDirectory.file("reports/official.tsv").get().asFile.absolutePath)
    maxHeapSize = "1536m"
}
val toolkitProbe by tasks.registering(JavaExec::class) {
    dependsOn(toolkit.classesTaskName)
    classpath = toolkit.runtimeClasspath
    mainClass = "io.github.sospd.engine.ToolkitProbe"
    args(layout.buildDirectory.file("reports/toolkit.tsv").get().asFile.absolutePath)
    maxHeapSize = "1536m"
}
tasks.register<JavaExec>("verifyOfficial") {
    group = "verification"
    description = "Compare independent official 4.0.2 generation with SoSPD over every challenge mask."
    dependsOn(officialProbe, toolkitProbe, tasks.classes)
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "io.github.sospd.verification.CompareReports"
    args(layout.buildDirectory.dir("reports").get().asFile.absolutePath)
}

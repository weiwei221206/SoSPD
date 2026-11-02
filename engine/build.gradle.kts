plugins { `java-library` }
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
sourceSets {
    main {
        java.setSrcDirs(listOf(
            "src/main/java",
            "../vendor/shpd/core/src/main/java",
            "../vendor/shpd/SPD-classes/src/main/java",
            "../vendor/shpd/services/src/main/java",
        ))
        resources.setSrcDirs(listOf("src/main/resources"))
    }
}
tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }
dependencies {
    api(project(":domain"))
    implementation("com.badlogicgames.gdx:gdx:1.14.0")
    implementation("com.badlogicgames.gdx:gdx-freetype:1.14.0")
    implementation("com.badlogicgames.gdx-controllers:gdx-controllers-core:2.2.4")
    implementation("org.json:json:20170516")
    testImplementation("junit:junit:4.13.2")
}


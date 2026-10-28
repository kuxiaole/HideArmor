plugins {
    java
}

group = "dev.hidearmor"
version = "1.1.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.codemc.io/repository/maven-releases/")
}

dependencies {
    compileOnly("dev.folia:folia-api:26.3.build.5-alpha")
    compileOnly("com.github.retrooper:packetevents-spigot:2.14.0")
    testImplementation("com.github.retrooper:packetevents-spigot:2.14.0")
    testImplementation("dev.folia:folia-api:26.3.build.5-alpha")
    testImplementation("io.netty:netty-transport:4.1.72.Final")
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}

tasks.processResources {
    filteringCharset = "UTF-8"
    inputs.property("version", project.version)
    filesMatching("plugin.yml") { expand("version" to project.version) }
}

tasks.test { useJUnitPlatform() }

tasks.jar { archiveBaseName.set("HideArmor") }

val sourceZip by tasks.registering(Zip::class) {
    archiveFileName.set("HideArmor-${project.version}-source.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    from(projectDir) {
        include("src/**", "docs/**", "gradle/**", "gradlew", "gradlew.bat",
                "build.gradle.kts", "settings.gradle.kts", "gradle.properties", ".gitignore", "README.md")
    }
}

tasks.assemble { dependsOn(sourceZip) }

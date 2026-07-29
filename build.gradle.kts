plugins {
    java
}

group = "de.raindancer"
version = "1.0.0"

// Compiled against 1.21.11 on purpose: the packs RRP manages declare pack formats for
// 1.21.11 *and* 26.x, so the plugin has to load on both. A 26.x API (Java 25 bytecode)
// would lock out every 1.21.11 server, which still runs on Java 21.
val paperApiVersion = "1.21.11-R0.1-SNAPSHOT"
val paperApiDeclaration = "1.21"

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Paper provides the API plus gson, snakeyaml, adventure and brigadier at runtime,
    // so RRP ships without a single shaded dependency.
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")

    testImplementation("io.papermc.paper:paper-api:$paperApiVersion")
    testImplementation(platform("org.junit:junit-bom:5.14.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.27.6")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // Java 21 bytecode: Paper 1.21.11 runs on Java 21, Paper 26.x on Java 25 — 21 loads on both.
    options.release = 21
    options.compilerArgs.add("-Xlint:all,-serial,-processing")
}

tasks.processResources {
    val props = mapOf("version" to project.version, "apiVersion" to paperApiDeclaration)
    inputs.properties(props)
    filesMatching("paper-plugin.yml") {
        expand(props)
    }
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

tasks.jar {
    archiveBaseName = "rrp"
    archiveClassifier = ""
    manifest {
        attributes(
            "Implementation-Title" to "Rain's Resourcepack Manager",
            "Implementation-Version" to project.version,
        )
    }
}

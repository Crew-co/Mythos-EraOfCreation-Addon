import org.gradle.api.tasks.Copy

plugins {
    kotlin("jvm") version "2.1.0"
    id("com.gradleup.shadow") version "8.3.11"
}

group = property("group") as String
version = property("version") as String

val foliaApiVersion = property("foliaApiVersion") as String

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")

    // Local dev without any GitHub token at all:  (in the Mythos repo) ./gradlew publishApiLocally
    mavenLocal()

    // The host's addon-api.
    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/${property("hostRepo")}")
        credentials {
            username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
            password = providers.gradleProperty("gpr.token").orNull ?: System.getenv("GITHUB_TOKEN")
        }
    }
}

dependencies {
    compileOnly("dev.folia:folia-api:$foliaApiVersion")

    // ONE dependency: the Mythos API. It carries both halves —
    //   net.crewco.mythos.addon/command/menu/hud  (the addon platform)
    //   net.crewco.mythos.api.*                   (roles, spirits, eras, powers, events)
    //
    // compileOnly, ALWAYS. The host provides these classes at runtime; a shaded copy is
    // a different class with the same name and every `instanceof` silently fails.
    compileOnly("${property("hostGroup")}:mythos-addon-api:${property("hostApiVersion")}")

    compileOnly(kotlin("stdlib"))
}

kotlin {
    jvmToolchain(21)
}

tasks {
    shadowJar {
        archiveBaseName.set(providers.gradleProperty("addonName").orElse(project.name))
        archiveClassifier.set("")
        // Nothing to relocate: every dependency here is compileOnly, provided by the
        // host at runtime. If you ever add a REAL library, relocate it — two addons
        // shipping different versions of the same lib will otherwise collide.
    }
    build { dependsOn(shadowJar) }
    jar { enabled = false }
}

tasks.register<Copy>("deployAddon") {
    group = "deployment"
    description = "Builds the addon and copies it into your test server's addons folder."
    dependsOn(tasks.shadowJar)

    val target = providers.gradleProperty("testServerPath").orNull
    onlyIf {
        if (target == null) logger.lifecycle("Set testServerPath in ~/.gradle/gradle.properties to use deployAddon.")
        target != null
    }
    from(tasks.shadowJar)
    if (target != null) into("$target/plugins/${property("hostPluginName")}/addons")
}

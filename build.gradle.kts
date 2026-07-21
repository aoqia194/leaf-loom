import com.diffplug.spotless.LineEnding
import groovy.json.JsonOutput
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jreleaser.model.Active
import org.jreleaser.model.Http
import java.net.URI
import java.util.Properties

// Project variables
var groupUrl = rootProject.group.toString().replace(".", "/")

// Environment variables
val env = System.getenv()!!
val isCiEnv = env["CI"].toBoolean()
// TODO(aoqia): Update gpg keys and maven info on the repositories
val gpgKeyPassphrase = env["GPG_PASSPHRASE_KEY"]
val gpgKeyPublic = env["GPG_PUBLIC_KEY"]
val gpgKeyPrivate = env["GPG_PRIVATE_KEY"]
val mavenUsername = env["MAVEN_USERNAME"]
val mavenPassword = env["MAVEN_PASSWORD"]

// We must build against the version of Kotlin Gradle ships with.
val props = Properties()
Project::class.java.classLoader.getResource("gradle-kotlin-dsl-versions.properties")!!
    .openStream().use { output ->
        props.load(output)
    }

val kotlinVersion: String? = props.getProperty("kotlin")
if (libs.versions.kotlin.get() != kotlinVersion) {
    throw IllegalStateException("Requires Kotlin version: $kotlinVersion")
}

repositories {
    maven {
        name = "Fabric"
        url = uri("https://maven.fabricmc.net/")
    }
    mavenCentral()
    gradlePluginPortal()
}

plugins {
    java
    idea
    eclipse
    groovy
//    codenarc
//    checkstyle
    alias(libs.plugins.kotlin)
    alias(libs.plugins.spotless)
    alias(libs.plugins.retry)

    // Generating the Gradle plugin marker POM
    alias(libs.plugins.gradle.plugin.publish)

    // Publishing to Maven Central
    `maven-publish`
    alias(libs.plugins.jreleaser)
}

base {
    archivesName = project.name
}

allprojects {
    if (!isCiEnv) {
        version = "${version}.local"
    }
}

configurations.configureEach {
    resolutionStrategy {
        failOnNonReproducibleResolution()
    }
}

configurations.named(JavaPlugin.API_ELEMENTS_CONFIGURATION_NAME) {
    attributes {
        attribute(
            GradlePluginApiVersion.GRADLE_PLUGIN_API_VERSION_ATTRIBUTE,
            objects.named(
                GradlePluginApiVersion::class.java,
                GradleVersion.current().version
            )
        )
    }
}

sourceSets {
    create("commonDecompiler") {
        java {
            srcDir("src/decompilers/common")
        }
    }

    create("fernflower") {
        java {
            srcDir("src/decompilers/fernflower")
        }
    }

    create("cfr") {
        java {
            srcDir("src/decompilers/cfr")
        }
    }

    create("vineflower") {
        java {
            srcDir("src/decompilers/vineflower")
        }
    }
}

dependencies {
    implementation(gradleApi())

    // libraries
    implementation(libs.commons.io)
    implementation(libs.gson)
    implementation(libs.bundles.asm)

    // game handling utils
    implementation(libs.fabric.stitch) {
        exclude(module = "enigma")
    }

    // tinyfile management
    implementation(libs.fabric.clazz.tweaker)
    implementation(libs.fabric.tiny.remapper)
    implementation(libs.fabric.mapping.io)
    implementation(libs.fabric.lorenz.tiny) {
        isTransitive = false
    }

    implementation(libs.fabric.loom.nativelib)

    // decompilers
    "fernflowerCompileOnly"(runtimeLibs.fernflower)
    "fernflowerCompileOnly"(libs.fabric.mapping.io)

    "cfrCompileOnly"(runtimeLibs.cfr)
    "cfrCompileOnly"(libs.fabric.mapping.io)

    "vineflowerCompileOnly"(runtimeLibs.vineflower)
    "vineflowerCompileOnly"(libs.fabric.mapping.io)

    "fernflowerApi"(sourceSets.named("commonDecompiler").get().output)
    "cfrApi"(sourceSets.named("commonDecompiler").get().output)
    "vineflowerApi"(sourceSets.named("commonDecompiler").get().output)

    implementation(sourceSets.named("commonDecompiler").get().output)
    implementation(sourceSets.named("fernflower").get().output)
    implementation(sourceSets.named("cfr").get().output)
    implementation(sourceSets.named("vineflower").get().output)

    // source code remapping
    implementation(libs.fabric.mercury)

    implementation(libs.fabric.unpick)
    implementation(libs.fabric.unpick.utils)

    // Kotlin
    implementation(libs.kotlin.metadata) {
        isTransitive = false
    }

    // Kapt integration
    compileOnly(libs.kotlin.gradle.plugin)

    // Testing
    testImplementation(gradleTestKit())
    testImplementation(testLibs.spock) {
        exclude(module = "groovy-all")
    }
    testImplementation(testLibs.junit.jupiter.engine)
    testRuntimeOnly(testLibs.junit.platform.launcher)
    testImplementation(testLibs.javalin) {
        exclude(group = "org.jetbrains.kotlin")
    }
    testImplementation(testLibs.mockito)
    testImplementation(testLibs.java.debug)
    testImplementation(testLibs.bcprov)
    testImplementation(testLibs.bcutil)
    testImplementation(testLibs.bcpkix)
    testImplementation(testLibs.fabric.loader)

    compileOnly(runtimeLibs.jetbrains.annotations)
    testCompileOnly(runtimeLibs.jetbrains.annotations)

    testCompileOnly(testLibs.mixin) {
        isTransitive = false
    }
}

java {
    withSourcesJar()
    withJavadocJar()

    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 21
}

tasks.jar {
    manifest {
        attributes("Implementation-Version" to project.version)
    }

    from(sourceSets.named("commonDecompiler").get().output.classesDirs)
    from(sourceSets.named("cfr").get().output.classesDirs)
    from(sourceSets.named("fernflower").get().output.classesDirs)
    from(sourceSets.named("vineflower").get().output.classesDirs)
}

tasks.javadoc {
    if (JavaVersion.current().isJava9Compatible) {
        (options as CoreJavadocOptions).addBooleanOption("html5", true)
    }
}

tasks.wrapper {
    distributionType = Wrapper.DistributionType.ALL
}

tasks.register("downloadGradleSources") {
    description = "Downloads the Gradle API sources next to the API jar." +
        "May require you to manually attach the sources jar."
    // TODO(aoqia): Implement in Kotlin

    doLast {
        // Awful hack to find the gradle api location
        val gradleApiFile = project.configurations.detachedConfiguration(dependencies.gradleApi()).files.find {
            it.name.startsWith("gradle-api")
        }

        val gradleApiSources = File(gradleApiFile!!.absolutePath.replace(".jar", "-sources.jar"))
        val url = "https://services.gradle.org/distributions/gradle-${GradleVersion.current().version}-src.zip"

        gradleApiSources.delete()

        println("Downloading (${url}) to (${gradleApiSources})")
        gradleApiSources.outputStream().use { out ->
            URI.create(url).toURL().openStream().use { it.copyTo(out) }
        }
    }
}

tasks.named<Test>("test") {
    description = "Run tests"

    maxHeapSize = "2560m"
    jvmArgs = listOf("-XX:+HeapDumpOnOutOfMemoryError")
    useJUnitPlatform()

    // Forward system prop onto tests.
    val prop = System.getProperty("leaf.${rootProject.name}.test.homeDir")
    if (prop != null && prop.isNotEmpty()) {
        systemProperty("leaf.${rootProject.name}.test.homeDir", prop)
    }

    if (isCiEnv) {
        retry {
            maxRetries = 3
        }
    }

    testLogging {
        // Log everything to the console
        setEvents(TestLogEvent.values().toList())
    }
}

tasks.register<PrintActionsTestName>("printActionsTestName") {
    description = "Replaces invalid characters in test names for GitHub Actions artifacts."
}

tasks.register("writeActionsTestMatrix") {
    description = "Outputs a JSON file with a list of all the tests to run."

    doLast {
        val testMatrix = mutableListOf<String>()
        val extendedTests = System.getenv("EXTENDED_TESTS").toBoolean()

        file("src/test/groovy/${groupUrl}/${rootProject.name}/test/integration").listFiles()?.forEach {
            if (it.name.endsWith("Test.groovy")) {
                if (it.name.endsWith("ReproducibleBuildTest.groovy")) {
                    // This test gets a special case to run across all os's
                    return@forEach
                }

                if (it.name.endsWith("DebugLineNumbersTest.groovy") && !extendedTests) {
                    // Known flakey test
                    return@forEach
                }

                val className = it.name.replace(".groovy", "")
                testMatrix.add("${rootProject.group}.${rootProject.name}.test.integration.${className}")
            }
        }

        // Run all the unit tests together
        testMatrix.add("net.fabricmc.loom.test.unit.*")

        val output = file("build/test_matrix.json")
        output.parentFile.mkdir()
        output.writeText(JsonOutput.toJson(testMatrix))
    }
}

// Workaround https://github.com/gradle/gradle/issues/25898
tasks.withType<Test>().configureEach {
    jvmArgs = listOf(
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.util=ALL-UNNAMED",
        "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED",
        "--add-opens=java.base/java.net=ALL-UNNAMED"
    )
}

spotless {
    setLineEndings(LineEnding.UNIX)

    java {
        targetExclude("**/generated/**")

        importOrder("java|javax", "", "\\#", "$group", "\\#$group")
        removeUnusedImports()
        forbidWildcardImports()

        cleanthat()
        eclipse().configFile(file("eclipse-formatter.xml"))
        // NOTE(aoqia): It's a nice feature but it causes some issues, so it will remain disabled for now.
//            .sortMembersEnabled(true)
        formatAnnotations()

        trimTrailingWhitespace()
        endWithNewline()
        licenseHeaderFile(file("HEADER")).yearSeparator("-")
    }

    groovy {
        importOrder("java|javax", "groovy", "", "\\#", "$group", "\\#$group")

        trimTrailingWhitespace()
        endWithNewline()
        licenseHeaderFile(file("HEADER")).yearSeparator("-")
        removeSemicolons()

        greclipse()
    }

    groovyGradle {
        target("src/**/*.gradle", "*.gradle")
        // Exclude build.gradle because it keeps pestering me about it!
        targetExclude("**/build.gradle")

        trimTrailingWhitespace()
        endWithNewline()
        greclipse()
    }

    kotlin {
        targetExclude("**/build.gradle.kts")
        targetExclude("src/test/resources/projects/*/**")

        ktlint()

        trimTrailingWhitespace()
        endWithNewline()
        licenseHeaderFile(file("HEADER")).yearSeparator("-")
    }
}

// TODO(aoqia): Enable and setup CodeNarc after initial formatting
// codenarc {
//     configFile = file("codenarc.groovy")
// }

// TODO(aoqia): Setup checkstyle
// checkstyle {
//     configFile = file("checkstyle.xml")
// }

gradlePlugin {
    website = property("url").toString()
    vcsUrl = property("url").toString()

    plugins {
        create("leafLoom") {
            id = "${rootProject.group}.${rootProject.name}"
            implementationClass = "${rootProject.group}.${rootProject.name}.LoomGradlePlugin"
            displayName = rootProject.name
            tags = listOf("projectzomboid", "zomboid", "leaf")
        }
        create("leafLoomCompanion") {
            id = "${rootProject.group}.${rootProject.name}-companion"
            implementationClass = "${rootProject.group}.${rootProject.name}.LoomCompanionGradlePlugin"
            displayName = "${rootProject.name}-companion"
            tags = listOf("projectzomboid", "zomboid", "leaf")
        }
    }
}

publishing {
    // TODO(aoqia): Wrap this in an if CI to stop configuring publishing unnecessarily
    publications.withType<MavenPublication>().configureEach {
        pom {
            name = rootProject.name
            group = rootProject.group
            description = rootProject.description
            url = property("url").toString()
            inceptionYear = "2025"

            developers {
                developer {
                    id = "aoqia"
                    name = "aoqia"
                }
            }

            issueManagement {
                system = "GitHub"
                url = "${property("url").toString()}/issues"
            }

            licenses {
                license {
                    name = "MIT"
                    url = "https://spdx.org/licenses/MIT.html"
                }
            }

            scm {
                connection = "scm:git:${property("url").toString()}.git"
                developerConnection =
                    "scm:git:${property("url").toString().replace("https", "ssh")}.git"
                url = property("url").toString()
            }
        }
    }

    repositories {
        maven {
            url = uri(layout.buildDirectory.dir("staging-deploy"))
        }
    }
}

jreleaser {
    project {
        name = rootProject.name
        version = rootProject.version.toString()
        versionPattern = "SEMVER"
        authors = listOf("aoqia194", "FabricMC")
        maintainers = listOf("aoqia194")
        license = "MIT"
        inceptionYear = "2025"

        links {
            homepage = property("url").toString()
            license = "https://spdx.org/licenses/MIT.html"
        }
    }

    signing {
        active = Active.ALWAYS
        armored = true
        passphrase = gpgKeyPassphrase
        publicKey = gpgKeyPublic
        secretKey = gpgKeyPrivate
    }

    deploy {
        maven {
            pomchecker {
                version = "1.14.0"
                failOnWarning = false // annoying
                failOnError = true
                strict = true
            }

            mavenCentral {
                create("sonatype") {
                    applyMavenCentralRules = true
                    active = Active.ALWAYS
                    snapshotSupported = true
                    authorization = Http.Authorization.BEARER
                    username = mavenUsername
                    password = mavenPassword
                    url = "https://central.sonatype.com/api/v1/publisher"
                    stagingRepository("build/staging-deploy")
                    verifyUrl = "https://repo1.maven.org/maven2/{{path}}/{{filename}}"
                    namespace = rootProject.group.toString()
                    retryDelay = 60
                    maxRetries = 30

                    // Override the plugin marker artifact to disable maven jar checks.
                    artifactOverride {
                        groupId = "${rootProject.group}.${rootProject.name}"
                        artifactId = "${rootProject.group}.${rootProject.name}.gradle.plugin"
                        jar = false
                        sourceJar = false
                        javadocJar = false
                        verifyPom = true
                    }
                }
            }
        }
    }

    release {
        github {
            enabled = true
            repoOwner = "aoqia194"
            name = "leaf-${rootProject.name}"
            host = "github.com"
            releaseName = "{{tagName}}"

            sign = true
            overwrite = true
            uploadAssets = Active.ALWAYS
            artifacts = true
            checksums = true
            signatures = true

            changelog {
                formatted = Active.ALWAYS
                preset = "conventional-commits"
                extraProperties.put("categorizeScopes", "true")
            }
        }
    }
}

apply(from = rootProject.file("gradle/versions.gradle"))

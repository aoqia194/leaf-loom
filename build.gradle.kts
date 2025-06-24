import org.apache.commons.io.FileUtils
import com.diffplug.spotless.LineEnding
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jreleaser.model.Active
import org.jreleaser.model.Http
import java.nio.file.Files
import java.time.LocalDate
import java.util.*
import kotlin.io.path.writeText

val env = System.getenv()!!
val isCiEnv = env["CI"].toBoolean()
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
    throw IllegalStateException("Requires Kotlin version: ${kotlinVersion}")
}

repositories {
    maven {
        name = "Fabric"
        url = uri("https://maven.fabricmc.net/")
    }
    gradlePluginPortal()
    mavenCentral()
}

plugins {
    java
    idea
    eclipse
    groovy

    alias(libs.plugins.kotlin)
    alias(libs.plugins.spotless)
    alias(libs.plugins.retry)

    // Publishing to Maven Central
    id("org.jreleaser") version "1.17.0"
    id("maven-publish")
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

    if (isCanBeConsumed) {
        attributes {
            attribute(GradlePluginApiVersion.GRADLE_PLUGIN_API_VERSION_ATTRIBUTE,
                objects.named(GradlePluginApiVersion::class.java, GradleVersion.current().getVersion()))
        }
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
    implementation(libs.guava)
    implementation(libs.bundles.asm)

    // game handling utils
    implementation(libs.fabric.stitch) {
        exclude(module = "enigma")
    }

    // tinyfile management
    implementation(libs.fabric.tiny.remapper)
    implementation(libs.fabric.access.widener)
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

    compileOnly(runtimeLibs.jetbrains.annotations)
    testCompileOnly(runtimeLibs.jetbrains.annotations)

    testCompileOnly(testLibs.mixin) {
        isTransitive = false
    }
}

java {
    withSourcesJar()
    withJavadocJar()

    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 17
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

generateVersionConstants(
    sourceSets.main.get(),
    "runtimeLibs",
    "${group.toString().replace(".", "/")}/${project.name}/util/LoomVersions"
)
generateVersionConstants(
    sourceSets.test.get(),
    "testLibs",
    "${group.toString().replace(".", "/")}/${project.name}/test/LoomTestVersions"
)

fun generateVersionConstants(sourceSet: SourceSet, catalogName: String, sourcesName: String) {
    val versionCatalog = extensions.getByType(VersionCatalogsExtension::class.java)
        .named(catalogName)

    val task = tasks.register(
        "${catalogName}GenerateConstants",
        GenerateVersions::class.java
    ) {
        versionCatalog.libraryAliases.forEach {
            val lib = versionCatalog.findLibrary(it).get().get()
            versions.put(it, lib.toString())
        }

        className = sourcesName
        headerFile = file("HEADER")
        outputDir = file("src/${sourceSet.name}/generated")
    }

    sourceSet.java.srcDir(task)
    tasks.named("compileKotlin").configure { dependsOn(task) }
    tasks.named("sourcesJar").configure { dependsOn(task) }
}

abstract class GenerateVersions : DefaultTask() {
    @get:Input
    abstract val versions: MapProperty<String, String>

    @get:Input
    abstract val className: Property<String>

    @get:InputFile
    abstract val headerFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun run() {
        val output = outputDir.get().asFile
        FileUtils.deleteDirectory(output)

        val className = className.get()
        val si = className.lastIndexOf("/")
        val packageName = className.substring(0, si)
        val packagePath = output.toPath().resolve(packageName)
        val sourceName = className.substring(si + 1, className.length)
        val sourcesPath = packagePath.resolve("${sourceName}.java")
        Files.createDirectories(packagePath)

        val constants = versions.get().map { (key, value) ->
            val split = value.split(":")
            if (split.size == 3) {
                "\tpublic static final $sourceName ${toSnakeCase(key)} = new $sourceName" +
                    "(\"${split[0]}\", \"${split[1]}\", \"${split[2]}\");"
            } else {
                ""
            }
        }.filter { it.isNotBlank() }.joinToString("\n")

        val header = headerFile.get().asFile.readText()
            .replace("\$YEAR", "${LocalDate.now().year}").trim()

        sourcesPath.writeText(
            """$header

package ${packageName.replace("/", ".")};

/**
 * Auto generated class, do not edit.
 */
public record ${sourceName}(String group, String module, String version) {
$constants

    public String mavenNotation() {
        return "%s:%s:%s".formatted(group, module, version);
    }
}
""".trimIndent())
    }

    fun toSnakeCase(input: String): String {
        return input.trim().replace(Regex("[^a-zA-Z0-9]+"), "_").uppercase()
    }
}

spotless {
    setLineEndings(LineEnding.UNIX)

    java {
        targetExclude("**/loom/util/DownloadUtil.java")
        targetExclude("**/generated/**")
        removeUnusedImports()
        indentWithSpaces(4)
        trimTrailingWhitespace()
        endWithNewline()
        licenseHeaderFile(rootProject.file("HEADER")).yearSeparator("-")
        //palantirJavaFormat()
    }

    groovy {
        indentWithSpaces(4)
        trimTrailingWhitespace()
        endWithNewline()
        importOrder("java", "javax", "", "groovy", "net.fabricmc", "", "${rootProject.group}", "", "\\#")
        licenseHeaderFile(rootProject.file("HEADER")).yearSeparator("-")
        greclipse()
    }

    groovyGradle {
        target("src/**/*.gradle", "*.gradle")
        // Exclude build.gradle because it keeps pestering me about it!
        targetExclude("**/build.gradle")
        indentWithSpaces(4)
        trimTrailingWhitespace()
        endWithNewline()
        greclipse()
    }

    kotlin {
        indentWithSpaces(4)
        trimTrailingWhitespace()
        endWithNewline()
        licenseHeaderFile(rootProject.file("HEADER")).yearSeparator("-")
        targetExclude("**/build.gradle.kts")
        targetExclude("src/test/resources/projects/*/**")
        ktlint()
    }
}

publishing {
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

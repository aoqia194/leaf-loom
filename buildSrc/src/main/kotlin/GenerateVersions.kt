import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

import java.time.LocalDate

import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskAction

/**
 * Generates a Java source file containing all the version from the Gradle version catalog.
 */
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
        output.deleteRecursively()

        val className = className.get()
        val si = className.lastIndexOf("/")
        val packageName = className.take(si)
        val packagePath = output.toPath().resolve(packageName)
        val sourceName = className.substring(si + 1, className.length)
        val sourcesPath = packagePath.resolve("${sourceName}.java")
        packagePath.createDirectories()

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
""".trimIndent()
        )
    }

    fun toSnakeCase(input: String): String {
        return input.trim().replace(Regex("[^a-zA-Z0-9]+"), "_").uppercase()
    }
}


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

generateVersionConstants(
    sourceSets.main,
    "runtimeLibs",
    "${group.toString().replace(".", "/")}/${project.name}/util/LoomVersions"
)

generateVersionConstants(
    sourceSets.test,
    "testLibs",
    "${group.toString().replace(".", "/")}/${project.name}/test/LoomTestVersions"
)

def generateVersionConstants(def sourceSet, def catalogName, def sourcesName) {
    def versionCatalog = extensions.getByType(VersionCatalogsExtension.class).named(catalogName)

            def task = tasks.register("${catalogName}GenerateConstants", GenerateVersions.class) {
        versionCatalog.getLibraryAliases().forEach {
            def lib = versionCatalog.findLibrary(it).get().get()
            getVersions().put(it, lib.toString())
        }

        className = sourcesName
        headerFile = file("HEADER")
        outputDir = file("src/${sourceSet.name}/generated")
    }

            sourceSet.java.srcDir task
            spotlessGroovyGradle.dependsOn task // Not quite sure why this is needed, but it fixes a warning.
            compileKotlin.dependsOn task
            sourcesJar.dependsOn task
}

// Because gradle makes it read only in build scripts
val name: String by settings
rootProject.name = name

pluginManagement {
    repositories {
        maven {
            name = "Fabric"
            url = uri("https://maven.fabricmc.net/")
        }
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
	versionCatalogs {
		create("testLibs") {
			from(files("gradle/test.libs.versions.toml"))
		}

		create("runtimeLibs") {
			from(files("gradle/runtime.libs.versions.toml"))
		}
	}
}

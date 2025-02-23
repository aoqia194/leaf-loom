// Because gradle makes it read only in build scripts
rootProject.name = name

dependencyResolutionManagement {
	versionCatalogs {
		testLibs {
			from(files("gradle/test.libs.versions.toml"))
		}
		runtimeLibs {
			from(files("gradle/runtime.libs.versions.toml"))
		}
	}
}

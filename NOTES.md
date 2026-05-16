This is a file to help track changes I've made to the original upstream code.
It will help me retrace my steps if I ever need to again in the future.

- Renamed a lot of package folders and class names to replace "Fabric" and game brand "Minecraft"
- Added separate client and server metadata providers
- Commented out usage of the FabricAPI in **LoomGradlePlugin**
- Removed the Mojang maven repository and all usages of it in **LoomRepositoryPlugin**
- Removed officialMojangMappings from **LoomGradleExtensionAPI**
- Removed official Mojang mapping layer in **LayeredMappingSpecBuilder**
- Removed parchment mapping layer in **LayeredMappingSpecBuilder**
- Added `onlyProvideJars` property to only set up the game provider jars
- Added support for loader JIJ in **InstallerData**
- Changed **ZomboidJarConfiguration#createZomboidProvider** to take in the split ^^
- Changed **RunConfig** to include workingDir and some custom args
- Removed Minecraft-specific args and added PZ args in **RunConfigSettings**
- Removed keeping JAR files that store game version in **SplitZomboidProvider**
- Removed minecraftExtractedServerJar and serverBundleMetadata because there is no wrapper JAR in PZ
- Removed Mojang jar verification in **ZomboidProvider**
- Changed manifest schema in **ZomboidVersionMeta**
- Removed library processors: **LWJGL2MavenLibraryProcessor**, **LWJGL3MavenLibraryProcessor**
- Removed `downloadAssets` task in **LoomTasks**
- Changed some stuff in **GenerateDLIConfigTask**
- Updated all the constants in **Constants**
- Added over the helper functions in **MirrorUtil**

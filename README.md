# Leaf Loom

A [Gradle](https://gradle.org/) plugin to setup a development environment for Project Zomboid mods. Primarily used in
the Leaf toolchain.

* Has built in support for tiny mappings (Used by [Yarn](https://github.com/aoqia194/leaf-yarn))
* Utilises the Fernflower and CFR decompilers to generate source code with comments.
* Designed to support modern versions of Project Zomboid (Tested with 41.78.16 and upwards)
* Built in support for IntelliJ IDEA, Eclipse and Visual Studio Code to generate run configurations for Zomboid.
* Loom targets the latest version of Gradle 7 or newer
* Supports Java 17 upwards

## Use Loom to develop mods

To get started developing your own mods please follow the guide
on [Setting up a mod development environment](https://fabricmc.net/wiki/tutorial:setup). Even though this guide is
originally for FabricMC/fabric, it willl also work for the most part here.

## Debugging Loom (Only needed if you want to work on Loom itself)

_This guide assumes you are using IntelliJ IDEA, other IDE's have not been tested; your experience may vary._

1. Import as a Gradle project by opening the build.gradle
2. Create a Gradle run configuration to run the following tasks `build publishToMavenLocal -x test`. This will build
   Loom and publish to a local maven repo without running the test suite. You can run it now.
3. Prepare a project for using the local version of Loom:
    * A good starting point is to clone the [fabric-example-mod](https://github.com/FabricMC/fabric-example-mod) into
      your working directory
    * Add `mavenLocal()` to the repositories:
        * If you're using `id 'leaf-loom'` inside `plugins`, the correct `repositories` block is
          inside `pluginManagement` in settings.gradle
        * If you're using `apply plugin:` for Loom, the correct `repositories` block is inside `buildscript` in
          build.gradle
    * Change the loom version to `0.6.local`. For example `id 'leaf-loom' version '0.6.local'`
4. Create a Gradle run configuration:
    * Set the Gradle project path to the project you have just configured above
    * Set some tasks to run, such as `clean build` you can change these to suit your needs.
    * Add the run configuration you created earlier to the "Before Launch" section to rebuild loom each time you debug
5. You should now be able to run the configuration in debug mode, with working breakpoints.

## Credits

I owe the FabricMC devs a small thanks here as the least I can do, as they helped me immensely.
Especially **modmuss**! I forgot I even wrote this here, but @modmuss yet again helped me. They deserve triple credits honestly.

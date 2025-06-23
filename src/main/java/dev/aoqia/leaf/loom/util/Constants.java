/*
 * This file is part of leaf-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016-2022 aoqia, FabricMC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package dev.aoqia.leaf.loom.util;

import java.nio.file.Path;

import org.gradle.internal.os.OperatingSystem;
import org.jetbrains.annotations.ApiStatus;
import org.objectweb.asm.Opcodes;

public class Constants {
    public static final String INDEX_MANIFEST_PATH =
        "https://raw.githubusercontent.com/aoqia194/leaf/refs/heads/main/indexes";
    public static final String VERSION_MANIFESTS =
        "https://raw.githubusercontent.com/aoqia194/leaf/refs/heads/main/manifests";
    public static final String FABRIC_REPOSITORY = "https://maven.fabricmc.net/";
    public static final int ASM_VERSION = Opcodes.ASM9;
    public static final Path CLIENT_INSTALL_PATH = Path.of("steamapps", "common", "ProjectZomboid");
    public static final Path SERVER_INSTALL_PATH = Path.of("steamapps", "common",
        "Project Zomboid Dedicated Server");

    private Constants() {}

    public static Path getDefaultSteamLibraryPath() {
        if (OperatingSystem.current() == OperatingSystem.MAC_OS) {
            return Path.of(System.getProperty("user.home"), "Library/Application Support/Steam");
        } else if (OperatingSystem.current() == OperatingSystem.LINUX) {
            return Path.of(System.getProperty("user.home"), ".local/share/Steam");
        }

        return Path.of("C:\\Program Files (x86)\\Steam");
    }

    public static Path getDefaultClientGamePath() {
        return getDefaultSteamLibraryPath().resolve(CLIENT_INSTALL_PATH);
    }

    public static Path getDefaultServerGamePath() {
        return getDefaultSteamLibraryPath().resolve(SERVER_INSTALL_PATH);
    }

    /**
     * Constants related to configurations.
     */
    public static final class Configurations {
        public static final String MOD_COMPILE_CLASSPATH = "modCompileClasspath";
        public static final String MOD_COMPILE_CLASSPATH_MAPPED = "modCompileClasspathMapped";
        public static final String INCLUDE = "include";
        public static final String INCLUDE_INTERNAL = "includeInternal";
        public static final String ZOMBOID = "zomboid";

        public static final String ZOMBOID_COMPILE_LIBRARIES = "zomboidLibraries";
        public static final String ZOMBOID_RUNTIME_LIBRARIES = "zomboidRuntimeLibraries";

        /**
         * These configurations contain the zomboid client libraries.
         */
        public static final String ZOMBOID_CLIENT_COMPILE_LIBRARIES = "zomboidClientLibraries";
        public static final String ZOMBOID_CLIENT_RUNTIME_LIBRARIES =
            "zomboidClientRuntimeLibraries";

        /**
         * Find the client only dependencies on the "zomboidLibraries" config.
         */
        public static final String ZOMBOID_SERVER_COMPILE_LIBRARIES = "zomboidServerLibraries";
        public static final String ZOMBOID_SERVER_RUNTIME_LIBRARIES =
            "zomboidServerRuntimeLibraries";

        public static final String ZOMBOID_EXTRACTED_LIBRARIES = "zomboidExtractedLibraries";
        public static final String ZOMBOID_NATIVES = "zomboidNatives";

        public static final String MAPPINGS = "mappings";
        public static final String MAPPINGS_FINAL = "mappingsFinal";
        public static final String LOADER_DEPENDENCIES = "loaderLibraries";
        public static final String LOOM_DEVELOPMENT_DEPENDENCIES = "loomDevelopmentDependencies";
        public static final String MAPPING_CONSTANTS = "mappingsConstants";
        public static final String UNPICK_CLASSPATH = "unpick";
        /**
         * A configuration that behaves like {@code runtimeOnly} but is not exposed in
         * {@code runtimeElements} to dependents. A bit like {@code testRuntimeOnly}, but for mods.
         */
        public static final String LOCAL_RUNTIME = "localRuntime";

        public static final String NAMED_ELEMENTS = "namedElements";

        private Configurations() {
        }
    }

    public static final class MixinArguments {
        public static final String IN_MAP_FILE_NAMED_INTERMEDIARY = "inMapFileNamedIntermediary";
        public static final String OUT_MAP_FILE_NAMED_INTERMEDIARY = "outMapFileNamedIntermediary";
        public static final String OUT_REFMAP_FILE = "outRefMapFile";
        public static final String DEFAULT_OBFUSCATION_ENV = "defaultObfuscationEnv";
        public static final String QUIET = "quiet";
        public static final String SHOW_MESSAGE_TYPES = "showMessageTypes";

        private MixinArguments() {
        }
    }

    public static final class Knot {
        public static final String KNOT_CLIENT = "dev.aoqia.leaf.loader.launch.knot.KnotClient";
        public static final String KNOT_SERVER = "dev.aoqia.leaf.loader.launch.knot.KnotServer";

        private Knot() {
        }
    }

    public static final class TaskGroup {
        public static final String LEAF = "leaf";
        public static final String IDE = "ide";

        private TaskGroup() {
        }
    }

    public static final class Task {
        public static final String PROCESS_INCLUDE_JARS = "processIncludeJars";

        private Task() {
        }
    }

    public static final class CustomModJsonKeys {
        public static final String INJECTED_INTERFACE = "loom:injected_interfaces";
        public static final String PROVIDED_JAVADOC = "loom:provided_javadoc";
    }

    public static final class Properties {
        public static final String ONLY_PROVIDE_JARS = "leaf.loom.onlyProvideJars";
        public static final String IGNORE_MISSING_FILES = "leaf.loom.ignoreMissingFiles";
        public static final String DONT_REMAP = "leaf.loom.dontRemap";
        public static final String DISABLE_REMAPPED_VARIANTS = "leaf.loom.disableRemappedVariants";
        public static final String DISABLE_PROJECT_DEPENDENT_MODS = "leaf.loom" +
                                                                    ".disableProjectDependentMods";
        public static final String LIBRARY_PROCESSORS = "leaf.loom.libraryProcessors";

        @ApiStatus.Experimental
        public static final String SANDBOX = "leaf.loom.experimental.sandbox";
        /**
         * When set the version of java that will be assumed that the game will run on, this
         * defaults to the current java version. Only set this when you have a good reason to do so,
         * the default should be fine for almost all cases.
         */
        public static final String RUNTIME_JAVA_COMPATIBILITY_VERSION = "leaf.loom" +
                                                                        ".runtimeJavaCompatibilityVersion";
        public static final String DECOMPILE_CACHE_MAX_FILES = "leaf.loom.decompileCacheMaxFiles";
        public static final String DECOMPILE_CACHE_MAX_AGE = "leaf.loom.decompileCacheMaxAge";
    }

    public static final class Manifest {
        public static final String PATH = "META-INF/MANIFEST.MF";

        public static final String REMAP_KEY = "Leaf-Loom-Remap";
        public static final String MIXIN_REMAP_TYPE = "Leaf-Loom-Mixin-Remap-Type";
        public static final String MAPPING_NAMESPACE = "Leaf-Mapping-Namespace";
        public static final String SPLIT_ENV = "Leaf-Loom-Split-Environment";
        public static final String SPLIT_ENV_NAME = "Leaf-Loom-Split-Environment-Name";
        public static final String CLIENT_ENTRIES = "Leaf-Loom-Client-Only-Entries";
        public static final String JAR_TYPE = "Leaf-Jar-Type";
        public static final String GRADLE_VERSION = "Leaf-Gradle-Version";
        public static final String LOOM_VERSION = "Leaf-Loom-Version";
        public static final String MIXIN_COMPILE_EXTENSIONS_VERSION = "Leaf-Mixin-Compile" +
                                                                      "-Extensions-Version";
        public static final String ZOMBOID_VERSION = "Leaf-Zomboid-Version";
        public static final String TINY_REMAPPER_VERSION = "Leaf-Tiny-Remapper-Version";
        public static final String LEAF_LOADER_VERSION = "Leaf-Loader-Version";
        public static final String MIXIN_VERSION = "Leaf-Mixin-Version";
        public static final String MIXIN_GROUP = "Leaf-Mixin-Group";
        public static final String KNOWN_IDY_BSMS = "Leaf-Loom-Known-Indy-BSMS";
    }
}

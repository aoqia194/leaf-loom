/*
 * This file is part of leaf-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016-2023 aoqia, FabricMC
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
package net.aoqia.loom.configuration.providers.mappings;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.*;
import java.util.Map;
import java.util.Objects;

import com.google.gson.JsonObject;
import net.aoqia.loom.LoomGradlePlugin;
import net.aoqia.loom.configuration.DependencyInfo;
import net.aoqia.loom.configuration.providers.mappings.tiny.TinyJarInfo;
import net.aoqia.loom.configuration.providers.zomboid.ZomboidProvider;
import net.aoqia.loom.util.Constants;
import net.aoqia.loom.util.DeletingFileVisitor;
import net.aoqia.loom.util.FileSystemUtil;
import net.aoqia.loom.util.ZipUtils;
import net.aoqia.loom.util.service.ServiceFactory;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.stitch.Command;
import net.fabricmc.stitch.commands.CommandProposeFieldNames;
import org.apache.tools.ant.util.StringUtils;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MappingConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(MappingConfiguration.class);

    public final String mappingsIdentifier;
    // The mappings we use in practice
    public final Path tinyMappings;
    public final Path tinyMappingsJar;
    private final Path mappingsWorkingDir;
    // The mappings that gradle gives us
    private final Path unpickDefinitions;

    private boolean hasUnpickDefinitions;
    private UnpickMetadata unpickMetadata;
    private Map<String, String> signatureFixes;

    private MappingConfiguration(String mappingsIdentifier, Path mappingsWorkingDir) {
        this.mappingsIdentifier = mappingsIdentifier;

        this.mappingsWorkingDir = mappingsWorkingDir;
        this.tinyMappings = mappingsWorkingDir.resolve("mappings.tiny");
        this.tinyMappingsJar = mappingsWorkingDir.resolve("mappings.jar");
        this.unpickDefinitions = mappingsWorkingDir.resolve("mappings.unpick");
    }

    public static MappingConfiguration create(
        Project project,
        ServiceFactory serviceFactory,
        DependencyInfo dependency,
        ZomboidProvider zomboidProvider) {
        final String version = dependency.getResolvedVersion();
        final Path inputJar = dependency
            .resolveFile()
            .orElseThrow(() -> new RuntimeException("Could not resolve mappings: " + dependency))
            .toPath();
        final String mappingsName = StringUtils.removeSuffix(
            dependency.getDependency().getGroup() + "."
            + dependency.getDependency().getName(),
            "-unmerged");

        final TinyJarInfo jarInfo = TinyJarInfo.get(inputJar);
        jarInfo.zomboidVersionId().ifPresent(id -> {
            if (!zomboidProvider.clientZomboidVersion().equals(id)) {
                LOGGER.warn("The mappings (%s) were not built for Zomboid version %s, proceed with caution."
                    .formatted(dependency.getDepString(), zomboidProvider.clientZomboidVersion()));
            }
        });

        final String mappingsIdentifier = createMappingsIdentifier(
            mappingsName,
            version,
            getMappingsClassifier(dependency, jarInfo.v2()),
            zomboidProvider.clientZomboidVersion());
        final Path workingDir = zomboidProvider.dir(mappingsIdentifier).toPath();

        var mappingProvider = new MappingConfiguration(mappingsIdentifier, workingDir);

        try {
            mappingProvider.setup(project, serviceFactory, zomboidProvider, inputJar);
        } catch (IOException e) {
            cleanWorkingDirectory(workingDir);
            throw new UncheckedIOException("Failed to setup mappings: " + dependency.getDepString(), e);
        }

        return mappingProvider;
    }

    private static String getMappingsClassifier(DependencyInfo dependency, boolean isV2) {
        String[] depStringSplit = dependency.getDepString().split(":");

        if (depStringSplit.length >= 4) {
            return "-" + depStringSplit[3] + (isV2 ? "-v2" : "");
        }

        return isV2 ? "-v2" : "";
    }

    private static boolean areMappingsV2(Path path) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            return MappingReader.detectFormat(reader) == MappingFormat.TINY_2_FILE;
        }
    }

    public static void extractMappings(Path jar, Path extractTo) throws IOException {
        try (FileSystemUtil.Delegate delegate = FileSystemUtil.getJarFileSystem(jar)) {
            extractMappings(delegate.fs(), extractTo);
        }
    }

    public static void extractMappings(FileSystem jar, Path extractTo) throws IOException {
        Files.copy(jar.getPath("mappings/mappings.tiny"), extractTo, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void cleanWorkingDirectory(Path mappingsWorkingDir) {
        try {
            if (Files.exists(mappingsWorkingDir)) {
                Files.walkFileTree(mappingsWorkingDir, new DeletingFileVisitor());
            }

            Files.createDirectories(mappingsWorkingDir);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String createMappingsIdentifier(
        String mappingsName, String version, String classifier, String minecraftVersion) {
        //          mappingsName      . mcVersion . version        classifier
        // Example: net.fabricmc.yarn . 1_16_5    . 1.16.5+build.5 -v2
        return mappingsName + "."
               + minecraftVersion.replace(' ', '_').replace('.', '_').replace('-', '_') + "." + version + classifier;
    }

    public TinyMappingsService getMappingsService(Project project, ServiceFactory serviceFactory) {
        return serviceFactory.get(getMappingsServiceOptions(project));
    }

    public Provider<TinyMappingsService.Options> getMappingsServiceOptions(Project project) {
        return TinyMappingsService.createOptions(project, Objects.requireNonNull(tinyMappings));
    }

    private void setup(Project project, ServiceFactory serviceFactory, ZomboidProvider zomboidProvider, Path inputJar)
        throws IOException {
        if (zomboidProvider.refreshDeps()) {
            cleanWorkingDirectory(mappingsWorkingDir);
        }

        if (Files.notExists(tinyMappings) || zomboidProvider.refreshDeps()) {
            storeMappings(project, serviceFactory, zomboidProvider, inputJar);
        } else {
            try (FileSystem fileSystem = FileSystems.newFileSystem(inputJar, (ClassLoader) null)) {
                extractExtras(fileSystem);
            }
        }

        if (Files.notExists(tinyMappingsJar) || zomboidProvider.refreshDeps()) {
            Files.deleteIfExists(tinyMappingsJar);
            ZipUtils.add(tinyMappingsJar, "mappings/mappings.tiny", Files.readAllBytes(tinyMappings));
        }
    }

    public void applyToProject(Project project, DependencyInfo dependency) {
        if (hasUnpickDefinitions()) {
            String notation = String.format(
                "%s:%s:%s:constants",
                dependency.getDependency().getGroup(),
                dependency.getDependency().getName(),
                dependency.getDependency().getVersion());

            project.getDependencies().add(Constants.Configurations.MAPPING_CONSTANTS, notation);
            populateUnpickClasspath(project);
        }

        project.getDependencies().add(Constants.Configurations.MAPPINGS_FINAL, project.files(tinyMappingsJar.toFile()));
    }

    public boolean hasUnpickDefinitions() {
        return hasUnpickDefinitions;
    }

    private void populateUnpickClasspath(Project project) {
        String unpickCliName = "unpick-cli";
        project.getDependencies()
            .add(
                Constants.Configurations.UNPICK_CLASSPATH,
                String.format(
                    "%s:%s:%s", unpickMetadata.unpickGroup, unpickCliName, unpickMetadata.unpickVersion));

        // Unpick ships with a slightly older version of asm, ensure it runs with at least the same version as loom.
        String[] asmDeps = new String[] {
            "org.ow2.asm:asm:%s", "org.ow2.asm:asm-tree:%s", "org.ow2.asm:asm-commons:%s", "org.ow2.asm:asm-util:%s"
        };

        for (String asm : asmDeps) {
            project.getDependencies()
                .add(
                    Constants.Configurations.UNPICK_CLASSPATH,
                    asm.formatted(Opcodes.class.getPackage().getImplementationVersion()));
        }
    }

    private void storeMappings(
        Project project, ServiceFactory serviceFactory, ZomboidProvider zomboidProvider, Path inputJar)
        throws IOException {
        LOGGER.info(":extracting " + inputJar.getFileName());

        try (FileSystemUtil.Delegate delegate = FileSystemUtil.getJarFileSystem(inputJar)) {
            extractMappings(delegate.fs(), tinyMappings);
            extractExtras(delegate.fs());
        }

        // Don't really need to support V1 for the time being (or foreseeable future).
        if (!areMappingsV2(tinyMappings)) {
            throw new UnsupportedOperationException("Mappings are using V1 which is unsupported currently.");
        }

        // V1 mappings
        //        if (!areMappingsV2(tinyMappings)) {
        //            final List<Path> zomboidJars = zomboidProvider.getZomboidJars();
        //
        //            if (zomboidJars.size() != 1) {
        //                throw new UnsupportedOperationException("V1 mappings only support single jar providers");
        //            }
        //
        //            // These are merged v1 mappings
        //            Files.deleteIfExists(tinyMappings);
        //            LOGGER.info(":populating field names");
        //            suggestFieldNames(zomboidJars.get(0), baseTinyMappings, tinyMappings);
        //        }
    }

    private void extractExtras(FileSystem jar) throws IOException {
        extractUnpickDefinitions(jar);
        extractSignatureFixes(jar);
    }

    private void extractUnpickDefinitions(FileSystem jar) throws IOException {
        Path unpickPath = jar.getPath("extras/definitions.unpick");
        Path unpickMetadataPath = jar.getPath("extras/unpick.json");

        if (!Files.exists(unpickPath) || !Files.exists(unpickMetadataPath)) {
            return;
        }

        Files.copy(unpickPath, unpickDefinitions, StandardCopyOption.REPLACE_EXISTING);

        unpickMetadata = parseUnpickMetadata(unpickMetadataPath);
        hasUnpickDefinitions = true;
    }

    private void extractSignatureFixes(FileSystem jar) throws IOException {
        Path recordSignaturesJsonPath = jar.getPath("extras/record_signatures.json");

        if (!Files.exists(recordSignaturesJsonPath)) {
            return;
        }

        try (Reader reader = Files.newBufferedReader(recordSignaturesJsonPath, StandardCharsets.UTF_8)) {
            //noinspection unchecked
            signatureFixes = LoomGradlePlugin.GSON.fromJson(reader, Map.class);
        }
    }

    private UnpickMetadata parseUnpickMetadata(Path input) throws IOException {
        JsonObject jsonObject =
            LoomGradlePlugin.GSON.fromJson(Files.readString(input, StandardCharsets.UTF_8), JsonObject.class);

        if (!jsonObject.has("version") || jsonObject.get("version").getAsInt() != 1) {
            throw new UnsupportedOperationException("Unsupported unpick version");
        }

        return new UnpickMetadata(
            jsonObject.get("unpickGroup").getAsString(),
            jsonObject.get("unpickVersion").getAsString());
    }

    private void suggestFieldNames(Path inputJar, Path oldMappings, Path newMappings) {
        Command command = new CommandProposeFieldNames();
        runCommand(
            command,
            inputJar.toFile().getAbsolutePath(),
            oldMappings.toAbsolutePath().toString(),
            newMappings.toAbsolutePath().toString());
    }

    private void runCommand(Command command, String... args) {
        try {
            command.run(args);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Path mappingsWorkingDir() {
        return mappingsWorkingDir;
    }

    public File getUnpickDefinitionsFile() {
        return unpickDefinitions.toFile();
    }

    @Nullable
    public Map<String, String> getSignatureFixes() {
        return signatureFixes;
    }

    public String getBuildServiceName(String name, String from, String to) {
        return "%s:%s:%s>%S".formatted(name, mappingsIdentifier(), from, to);
    }

    public String mappingsIdentifier() {
        return mappingsIdentifier;
    }

    public record UnpickMetadata(String unpickGroup, String unpickVersion) {
    }
}

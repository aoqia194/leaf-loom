/*
 * This file is part of leaf-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2024 aoqia, FabricMC
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
package dev.aoqia.leaf.loom.task.service;

import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import dev.aoqia.leaf.loom.LoomGradleExtension;
import dev.aoqia.leaf.loom.api.mappings.layered.MappingsNamespace;
import dev.aoqia.leaf.loom.configuration.providers.mappings.TinyMappingsService;
import dev.aoqia.leaf.loom.util.service.Service;
import dev.aoqia.leaf.loom.util.service.ServiceFactory;
import dev.aoqia.leaf.loom.util.service.ServiceType;
import net.fabricmc.lorenztiny.TinyMappingsJoiner;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.mercury.Mercury;
import org.cadixdev.mercury.remapper.MercuryRemapper;
import org.gradle.api.IllegalDependencyNotation;
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MigrateMappingsService extends Service<MigrateMappingsService.Options> {
    private static final Logger LOGGER = LoggerFactory.getLogger(MigrateMappingsService.class);
    private static final ServiceType<Options, MigrateMappingsService> TYPE =
            new ServiceType<>(Options.class, MigrateMappingsService.class);

    public MigrateMappingsService(Options options, ServiceFactory serviceFactory) {
        super(options, serviceFactory);
    }

    public static Provider<Options> createOptions(
            Project project, Provider<String> targetMappings, DirectoryProperty inputDir, DirectoryProperty outputDir) {
        LoomGradleExtension extension = LoomGradleExtension.get(project);
        final Provider<String> from = project.provider(() -> "official");
        final Provider<String> to = project.provider(() -> "named");
        final JavaVersion javaVersion =
                project.getExtensions().getByType(JavaPluginExtension.class).getSourceCompatibility();

        ConfigurableFileCollection classpath = project.getObjects().fileCollection();
        classpath.from(project.getConfigurations().getByName(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME));
        // Question: why are both of these needed?
        classpath.from(extension.getZomboidJars(MappingsNamespace.OFFICIAL));
        classpath.from(extension.getZomboidJars(MappingsNamespace.NAMED));

        return TYPE.create(project, (o) -> {
            FileCollection targetMappingsFile = getTargetMappingsFile(project, targetMappings.get());
            o.getSourceMappings().set(MappingsService.createOptionsWithProjectMappings(project, from, to));
            o.getTargetMappings()
                    .set(TinyMappingsService.createOptions(project, targetMappingsFile, "mappings/mappings.tiny"));
            o.getSourceCompatibility().set(javaVersion.toString());
            o.getInputDir().set(inputDir);
            o.getClasspath().from(classpath);
            o.getOutputDir().set(outputDir);
        });
    }

    /**
     * Return a mappings file for the requested mappings.
     */
    private static FileCollection getTargetMappingsFile(Project project, String mappings) {
        if (mappings == null || mappings.isEmpty()) {
            throw new IllegalArgumentException(
                    "No mappings were specified. Use --mappings=\"\" to specify target mappings");
        }

        try {
            Dependency dependency = project.getDependencies().create(mappings);
            return project.getConfigurations().detachedConfiguration(dependency);
        } catch (IllegalDependencyNotation ignored) {
            LOGGER.info("Could not locate mappings, presuming V2 Yarn");
            return project.getConfigurations()
                    .detachedConfiguration(project.getDependencies()
                            .create(ImmutableMap.of(
                                    "group", "net.fabricmc", "name", "yarn", "version", mappings, "classifier", "v2")));
        }
    }

    public void migrateMapppings() throws IOException {
        final Path inputDir = getOptions().getInputDir().get().getAsFile().toPath();
        final Path outputDir = getOptions().getOutputDir().get().getAsFile().toPath();

        if (!Files.exists(inputDir) || !Files.isDirectory(inputDir)) {
            throw new IllegalArgumentException("Could not find input directory: " + inputDir.toAbsolutePath());
        }

        Files.deleteIfExists(outputDir);
        Files.createDirectories(outputDir);

        Mercury mercury = new Mercury();
        mercury.setGracefulClasspathChecks(true);
        mercury.setSourceCompatibility(getOptions().getSourceCompatibility().get());

        final MappingsService sourceMappingsService =
                getServiceFactory().get(getOptions().getSourceMappings().get());
        final TinyMappingsService targetMappingsService =
                getServiceFactory().get(getOptions().getTargetMappings().get());

        final MappingSet mappingSet = new TinyMappingsJoiner(
                        sourceMappingsService.getMemoryMappingTree(),
                        MappingsNamespace.NAMED.toString(),
                        targetMappingsService.getMappingTree(),
                        MappingsNamespace.NAMED.toString(),
                        MappingsNamespace.OFFICIAL.toString())
                .read();

        mercury.getProcessors().add(MercuryRemapper.create(mappingSet));

        for (File file : getOptions().getClasspath().getFiles()) {
            mercury.getClassPath().add(file.toPath());
        }

        try {
            mercury.rewrite(inputDir, outputDir);
        } catch (Exception e) {
            LOGGER.warn("Could not remap fully!", e);
        }

        // clean file descriptors
        System.gc();
    }

    public interface Options extends Service.Options {
        @Nested
        Property<MappingsService.Options> getSourceMappings();

        @Nested
        Property<TinyMappingsService.Options> getTargetMappings();

        @InputDirectory
        DirectoryProperty getInputDir();

        @Input
        Property<String> getSourceCompatibility();

        @InputFiles
        ConfigurableFileCollection getClasspath();

        @OutputDirectory
        DirectoryProperty getOutputDir();
    }
}

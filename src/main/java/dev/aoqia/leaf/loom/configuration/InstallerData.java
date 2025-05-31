/*
 * This file is part of leaf-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021-2022 aoqia, FabricMC
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
package dev.aoqia.leaf.loom.configuration;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import dev.aoqia.leaf.loom.LoomGradleExtension;
import dev.aoqia.leaf.loom.LoomRepositoryPlugin;
import dev.aoqia.leaf.loom.configuration.ide.idea.IdeaUtils;
import dev.aoqia.leaf.loom.util.Constants;
import dev.aoqia.leaf.loom.util.FileSystemUtil;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.commons.io.FileSystem;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.plugins.JavaPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record InstallerData(String version, JsonObject installerJson) {
    private static final Logger LOGGER = LoggerFactory.getLogger(InstallerData.class);

    public void applyToProject(Project project) {
        LoomGradleExtension extension = LoomGradleExtension.get(project);

        if (extension.getInstallerData() != null) {
            throw new IllegalStateException("Already applied installer data");
        }
        extension.setInstallerData(this);

        final JsonObject libraries = installerJson.get("libraries").getAsJsonObject();
        applyDependendencies(libraries.get("common").getAsJsonArray(), project);

        // Apply development dependencies if they exist.
        if (libraries.has("development")) {
            applyDependendencies(libraries.get("development").getAsJsonArray(), project);
        }
    }

    private void applyDependendencies(JsonArray jsonArray, Project project) {
        LoomGradleExtension extension = LoomGradleExtension.get(project);
        Configuration loaderDepsConfig = project.getConfigurations()
            .getByName(Constants.Configurations.LOADER_DEPENDENCIES);
        Configuration annotationProcessor = project.getConfigurations()
            .getByName(JavaPlugin.ANNOTATION_PROCESSOR_CONFIGURATION_NAME);

        boolean appliedJijJars = false;
        for (JsonElement jsonElement : jsonArray) {
            final JsonObject jsonObject = jsonElement.getAsJsonObject();
            final String name = jsonObject.get("name").getAsString();

            if (jsonObject.has("file")) {
                final URI file = URI.create(jsonObject.get("file").getAsString());

                if (file.getScheme().equalsIgnoreCase("loader")) {
                    if (appliedJijJars) {
                        break;
                    }

                    File loaderJar = null;

                    final var cfg = project.getConfigurations().getByName("modImplementation");
                    if (!cfg.isCanBeResolved()) {
                        throw new RuntimeException(
                            String.format("Skipping configuration (%s) because it cant be resolved",
                                cfg.getName()));
                    }
                    var result = cfg.getIncoming()
                        .getArtifacts()
                        .getArtifacts()
                        .stream()
                        .filter(artifact -> artifact.getId()
                            .getComponentIdentifier()
                            .toString()
                            .startsWith("dev.aoqia.leaf:loader"))
                        .toList();

                    if (!result.isEmpty()) {
                        loaderJar = result.get(0).getFile();
                    }

                    if (loaderJar == null) {
                        throw new RuntimeException(
                            String.format("Expected loader jar with string (%s) but found nothing.",
                                file));
                    }

                    final String walkPath = "/META-INF/jars";
                    try (var fs = FileSystemUtil.getJarFileSystem(loaderJar, false);
                         var walk = Files.walk(fs.getPath(walkPath), 1)) {
                        for (var iterator = walk.iterator(); iterator.hasNext(); ) {
                            Path entry = iterator.next();

                            // Files.walk includes the start path too, so skip it. :c
                            if (entry.toString().equals(walkPath)) {
                                continue;
                            }

                            Path src = fs.getPath("/").resolve(entry);
                            Path srcFile = src.getFileName(); // is jars, not the jar itself
                            Path dest = extension.getFiles()
                                .getProjectBuildCache()
                                .toPath()
                                .resolve(FileSystem.getCurrent()
                                    .normalizeSeparators(srcFile.toString()));

                            // TODO: Maybe make this not replace existing if it takes too long.
                            Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);

                            Dependency modDep = project.getDependencies()
                                .create(project.files(dest));
                            addDependency(modDep, extension, annotationProcessor,
                                loaderDepsConfig);
                        }
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to extract loader JiJ jars", e);
                    }

                    appliedJijJars = true;
                }
            } else {
                ExternalModuleDependency modDep = (ExternalModuleDependency)
                    project.getDependencies().create(name);

                // Match the launcher in not being transitive
                modDep.setTransitive(false);
                addDependency(modDep, extension, annotationProcessor, loaderDepsConfig);
            }

            // If user choose to use dependencyResolutionManagement, then they should declare
            // these repositories manually in the settings file.
            if (project.getGradle().getPlugins().hasPlugin(LoomRepositoryPlugin.class)) {
                continue;
            }

            addRepository(jsonObject, project);
        }
    }

    private void addDependency(Dependency dep, LoomGradleExtension extension, Configuration ap,
        Configuration loaderDeps) {
        loaderDeps.getDependencies().add(dep);

        // Work around https://github.com/FabricMC/Mixin/pull/60 and
        // https://github.com/FabricMC/fabric-mixin-compile-extensions/issues/14.
        if (!IdeaUtils.isIdeaSync() && extension.getMixin().getUseLegacyMixinAp().get()) {
            ap.getDependencies().add(dep);
        }
    }

    private void addRepository(JsonObject jsonObject, Project project) {
        if (!jsonObject.has("url")) {
            return;
        }

        final String url = jsonObject.get("url").getAsString();
        final boolean isPresent = project.getRepositories().stream()
            .filter(artifactRepository -> artifactRepository instanceof MavenArtifactRepository)
            .map(artifactRepository -> (MavenArtifactRepository) artifactRepository)
            .anyMatch(mavenArtifactRepository ->
                mavenArtifactRepository.getUrl().toString().equalsIgnoreCase(url));

        if (isPresent) {
            return;
        }

        project.getRepositories()
            .maven(mavenArtifactRepository ->
                mavenArtifactRepository.setUrl(jsonObject.get("url").getAsString()));
    }
}

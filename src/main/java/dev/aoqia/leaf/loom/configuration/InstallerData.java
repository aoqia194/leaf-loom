/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021-2022 FabricMC
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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import dev.aoqia.leaf.loom.LoomGradlePlugin;
import dev.aoqia.leaf.loom.util.FileSystemUtil;

import org.apache.commons.io.FileSystem;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.plugins.JavaPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.aoqia.leaf.loom.LoomGradleExtension;
import dev.aoqia.leaf.loom.LoomRepositoryPlugin;
import dev.aoqia.leaf.loom.configuration.ide.idea.IdeaUtils;
import dev.aoqia.leaf.loom.util.Constants;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public record InstallerData(String version, JsonObject installerJson) {
    public static final String INSTALLER_PATH = "leaf-installer.json";
	private static final Logger LOGGER = LoggerFactory.getLogger(InstallerData.class);

    public static InstallerData fromBytes(byte[] bytes, String version) {
        return new InstallerData(version,
            LoomGradlePlugin.GSON.fromJson(new String(bytes, StandardCharsets.UTF_8), JsonObject.class));
    }

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
		Configuration loaderDepsConfig = project.getConfigurations().getByName(Constants.Configurations.LOADER_DEPENDENCIES);
		Configuration annotationProcessor = project.getConfigurations().getByName(JavaPlugin.ANNOTATION_PROCESSOR_CONFIGURATION_NAME);

        boolean hasAppliedJij = false;
		for (JsonElement jsonElement : jsonArray) {
			final JsonObject jsonObject = jsonElement.getAsJsonObject();
			final String name = jsonObject.get("name").getAsString();

			LOGGER.debug("Adding dependency ({}) from installer JSON", name);

            // Support loader JIJs that need to be extracted and added as dependency.
            // NOTE(leaf): Unused for now, as this is not needed currently.
            if (false && jsonObject.has("file")) {
                final URI file = URI.create(jsonObject.get("file").getAsString());

                if (!file.getScheme().equalsIgnoreCase("loader")) {
                    continue;
                }

                if (hasAppliedJij) {
                    break;
                }

                File loaderJar = null;
                final var cfg = project.getConfigurations().getByName("modImplementation");
                if (!cfg.isCanBeResolved()) {
                    throw new RuntimeException("Skipping configuration %s because it can't be resolved"
                        .formatted(cfg.getName()));
                }
                var result = cfg.getIncoming().getArtifacts().getArtifacts().stream().filter(
                    a -> a.getId().getComponentIdentifier().toString().startsWith("dev.aoqia.leaf:loader")
                ).toList();

                if (!result.isEmpty()) {
                    loaderJar = result.getFirst().getFile();
                }

                if (loaderJar == null) {
                    throw new RuntimeException("Expected loader jar with string %s but found nothing".formatted(file));
                }

                if (!FileSystemUtil.isFileLocked(loaderJar)) {
                    final String walkPath = "/META-INF/jars";
                    try (var fs = FileSystemUtil.getJarFileSystem(loaderJar, false);
                    var walk = Files.walk(fs.getPath(walkPath), 1)) {
                        for (var iter = walk.iterator(); iter.hasNext();) {
                            Path entry = iter.next();

                            // Files.walk includes the start path too, skip it.
                            if (entry.toString().equals(walkPath)) {
                                continue;
                            }

                            Path src = fs.getPath("/").resolve(entry);
                            Path srcFile = src.getFileName();
                            Path dest = extension.getFiles().getProjectBuildCache().toPath()
                                .resolve(FileSystem.getCurrent().normalizeSeparators(srcFile.toString()));

                            if (!dest.toFile().exists()) {
                                Files.copy(src, dest);

                                Dependency modDep = project.getDependencies().create(project.files(dest));
                                addDependency(modDep, extension, annotationProcessor, loaderDepsConfig);
                            }
                        }
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to extract loader JIJ jars", e);
                    }
                } else {
                    LOGGER.warn("Failed to extract loader JIJ jars because the jar is locked."
                        + " If you are hotswapping classes, safely ignore this warning.");
                }

                hasAppliedJij = true;
            } else {
                ExternalModuleDependency modDep = (ExternalModuleDependency) project.getDependencies().create(name);
                modDep.setTransitive(false); // Match the launcher in not being transitive
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

	private void addRepository(JsonObject jsonObject, Project project) {
		if (!jsonObject.has("url")) {
			return;
		}

		final String url = jsonObject.get("url").getAsString();
		final boolean isPresent = project.getRepositories().stream()
				.filter(artifactRepository -> artifactRepository instanceof MavenArtifactRepository)
				.map(artifactRepository -> (MavenArtifactRepository) artifactRepository)
				.anyMatch(mavenArtifactRepository -> mavenArtifactRepository.getUrl().toString().equalsIgnoreCase(url));

		if (isPresent) {
			return;
		}

		project.getRepositories().maven(mavenArtifactRepository -> mavenArtifactRepository.setUrl(jsonObject.get("url").getAsString()));
	}

    private void addDependency(Dependency dep, LoomGradleExtension extension, Configuration ap, Configuration loaderDeps) {
        loaderDeps.getDependencies().add(dep);

        // Work around https://github.com/FabricMC/Mixin/pull/60 and https://github.com/FabricMC/fabric-mixin-compile-extensions/issues/14.
        if (!IdeaUtils.isIdeaSync() && extension.getMixin().getUseLegacyMixinAp().get()) {
            ap.getDependencies().add(dep);
        }
    }
}

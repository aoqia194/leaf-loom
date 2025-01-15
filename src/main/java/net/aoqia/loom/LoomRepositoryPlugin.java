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
package net.aoqia.loom;

import net.aoqia.loom.extension.LoomFiles;
import net.aoqia.loom.util.MirrorUtil;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ArtifactRepositoryContainer;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.initialization.Settings;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.PluginAware;
import org.jetbrains.annotations.NotNull;

public class LoomRepositoryPlugin implements Plugin<PluginAware> {
    public static void forceLWJGLFromMavenCentral(RepositoryHandler repositories) {
        if (repositories.findByName("MavenCentralLWJGL") != null) {
            // Already applied.
            return;
        }

        // Force LWJGL from central, as it contains all the platform natives.
        MavenArtifactRepository central = repositories.maven(repo -> {
            repo.setName("MavenCentralLWJGL");
            repo.setUrl(ArtifactRepositoryContainer.MAVEN_CENTRAL_URL);
            repo.content(content -> {
                content.includeGroup("org.lwjgl");
            });
        });

        repositories.exclusiveContent(repository -> {
            repository.forRepositories(central);
            repository.filter(filter -> {
                filter.includeGroup("org.lwjgl");
            });
        });
    }

    @Override
    public void apply(@NotNull PluginAware target) {
        if (target instanceof Settings settings) {
            declareRepositories(
                settings.getDependencyResolutionManagement().getRepositories(),
                LoomFiles.create(settings),
                settings);

            // leave a marker so projects don't try to override these
            settings.getGradle().getPluginManager().apply(LoomRepositoryPlugin.class);
        } else if (target instanceof Project project) {
            if (project.getGradle().getPlugins().hasPlugin(LoomRepositoryPlugin.class)) {
                return;
            }

            declareRepositories(project.getRepositories(), LoomFiles.create(project), project);
        } else if (target instanceof Gradle) {
        } else {
            throw new IllegalArgumentException(
                "Expected target to be a Project or Settings, but was a " + target.getClass());
        }
    }

    private void declareRepositories(RepositoryHandler repositories, LoomFiles files, ExtensionAware target) {
        declareLocalRepositories(repositories, files);

        repositories.maven(repo -> {
            repo.setName("Fabric");
            repo.setUrl(MirrorUtil.getFabricRepository(target));
        });

        repositories.mavenCentral();
    }

    private void declareLocalRepositories(RepositoryHandler repositories, LoomFiles files) {
        repositories.maven(repo -> {
            repo.setName("LoomLocalRemappedMods");
            repo.setUrl(files.getRemappedModCache());
        });

        repositories.maven(repo -> {
            repo.setName("LoomGlobalZomboid");
            repo.setUrl(files.getGlobalZomboidRepo());
        });

        repositories.maven(repo -> {
            repo.setName("LoomLocalZomboid");
            repo.setUrl(files.getLocalZomboidRepo());
        });
    }
}

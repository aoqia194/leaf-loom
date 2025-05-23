/*
 * This file is part of leaf-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2018-2021 aoqia, FabricMC
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
package dev.aoqia.leaf.loom.configuration.providers.mappings;

import java.io.File;
import java.nio.file.Path;

import dev.aoqia.leaf.loom.LoomGradleExtension;
import dev.aoqia.leaf.loom.api.mappings.layered.MappingContext;
import dev.aoqia.leaf.loom.configuration.providers.zomboid.ZomboidProvider;
import dev.aoqia.leaf.loom.util.copygamefile.CopyGameFileBuilder;
import dev.aoqia.leaf.loom.util.download.DownloadBuilder;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.MinimalExternalModuleDependency;
import org.gradle.api.artifacts.ResolutionStrategy;
import org.gradle.api.logging.Logger;

public class GradleMappingContext implements MappingContext {
    private final Project project;
    private final LoomGradleExtension extension;
    private final String workingDirName;

    public GradleMappingContext(Project project, String workingDirName) {
        this.project = project;
        this.extension = LoomGradleExtension.get(project);
        this.workingDirName = workingDirName;
    }

    public Project getProject() {
        return project;
    }

    public LoomGradleExtension getExtension() {
        return extension;
    }

    @Override
    public Path resolveDependency(Dependency dependency) {
        Configuration configuration = project.getConfigurations().detachedConfiguration(dependency);
        // Don't allow changing versions as this breaks down with how we cache layered mappings.
        configuration.resolutionStrategy(ResolutionStrategy::failOnNonReproducibleResolution);
        return configuration.getSingleFile().toPath();
    }


    @Override
    public Path resolveMavenDependency(String mavenNotation) {
        return resolveDependency(project.getDependencies().create(mavenNotation));
    }

    @Override
    public Path resolveDependency(MinimalExternalModuleDependency dependency) {
        return resolveDependency(project.getDependencies().create(dependency));
    }

    @Override
    public ZomboidProvider zomboidProvider() {
        return extension.getZomboidProvider();
    }

    @Override
    public Path workingDirectory(String name) {
        return new File(zomboidProvider().dir("layered/working_dir/" + workingDirName), name).toPath();
    }

    @Override
    public Logger getLogger() {
        return project.getLogger();
    }

    @Override
    public CopyGameFileBuilder copyGameFile(String url) {
        return extension.copyGameFile(url);
    }

    @Override
    public DownloadBuilder download(String url) {
        return extension.download(url);
    }

    @Override
    public boolean refreshDeps() {
        return extension.refreshDeps();
    }
}

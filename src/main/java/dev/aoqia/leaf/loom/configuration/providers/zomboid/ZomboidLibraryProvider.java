/*
 * This file is part of leaf-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2018-2023 aoqia, FabricMC
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
package dev.aoqia.leaf.loom.configuration.providers.zomboid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import dev.aoqia.leaf.loom.LoomGradleExtension;
import dev.aoqia.leaf.loom.configuration.providers.zomboid.library.Library;
import dev.aoqia.leaf.loom.configuration.providers.zomboid.library.LibraryContext;
import dev.aoqia.leaf.loom.configuration.providers.zomboid.library.LibraryProcessorManager;
import dev.aoqia.leaf.loom.configuration.providers.zomboid.library.ZomboidLibraryHelper;
import dev.aoqia.leaf.loom.configuration.providers.zomboid.library.processors.RuntimeLog4jLibraryProcessor;
import dev.aoqia.leaf.loom.util.Constants;
import dev.aoqia.leaf.loom.util.Platform;
import dev.aoqia.leaf.loom.util.gradle.GradleUtils;
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.provider.Provider;

public class ZomboidLibraryProvider {
    private static final Platform platform = Platform.CURRENT;

    private final Project project;
    private final ZomboidProvider zomboidProvider;
    private final LibraryProcessorManager processorManager;

    public ZomboidLibraryProvider(ZomboidProvider zomboidProvider, Project project) {
        this.project = project;
        this.zomboidProvider = zomboidProvider;
        this.processorManager = new LibraryProcessorManager(
            platform,
            project.getRepositories(),
            LoomGradleExtension.get(project).getLibraryProcessors().get(),
            getEnabledProcessors());
    }

    private List<String> getEnabledProcessors() {
        final LoomGradleExtension extension = LoomGradleExtension.get(project);

        var enabledProcessors = new ArrayList<String>();

        if (extension.getRuntimeOnlyLog4j().get()) {
            enabledProcessors.add(RuntimeLog4jLibraryProcessor.class.getSimpleName());
        }

        final Provider<String> libraryProcessorsProperty =
            project.getProviders().gradleProperty(Constants.Properties.LIBRARY_PROCESSORS);

        if (libraryProcessorsProperty.isPresent()) {
            String[] split = libraryProcessorsProperty.get().split(":");
            enabledProcessors.addAll(Arrays.asList(split));
        }

        return Collections.unmodifiableList(enabledProcessors);
    }

    public void provide() {
        final LoomGradleExtension extension = LoomGradleExtension.get(project);
        final ZomboidJarConfiguration<?, ?, ?> jarConfiguration = extension.getZomboidJarConfiguration().get();

        final boolean provideClient = jarConfiguration.supportedEnvironments().contains("client");
        final boolean provideServer = jarConfiguration.supportedEnvironments().contains("server");
        assert provideClient || provideServer;

        if (provideClient) {
            provideClientLibraries();
        }

        if (provideServer) {
            provideServerLibraries();
        }
    }

    private void provideClientLibraries() {
        final List<Library> libraries =
            ZomboidLibraryHelper.getLibrariesForPlatform(zomboidProvider.getClientVersionInfo(), platform);
        final List<Library> processLibraries = processLibraries(libraries);
        processLibraries.forEach(this::applyClientLibrary);

        if (!zomboidProvider.getClientVersionInfo().hasNativesToExtract()) {
            project.getConfigurations()
                .named(
                    Constants.Configurations.ZOMBOID_RUNTIME_LIBRARIES,
                    configuration -> configuration.extendsFrom(
                        project.getConfigurations().getByName(Constants.Configurations.ZOMBOID_NATIVES)));
        }
    }

    private void provideServerLibraries() {
        final List<Library> libraries =
            ZomboidLibraryHelper.getLibrariesForPlatform(zomboidProvider.getServerVersionInfo(), platform);
        final List<Library> processLibraries = processLibraries(libraries);
        processLibraries.forEach(this::applyServerLibrary);

        if (!zomboidProvider.getServerVersionInfo().hasNativesToExtract()) {
            project.getConfigurations()
                .named(
                    Constants.Configurations.ZOMBOID_RUNTIME_LIBRARIES,
                    configuration -> configuration.extendsFrom(
                        project.getConfigurations().getByName(Constants.Configurations.ZOMBOID_NATIVES)));
        }
    }

    private List<Library> processLibraries(List<Library> libraries) {
        final LibraryContext libraryContext =
            new LibraryContext(zomboidProvider.getClientVersionInfo(), getTargetRuntimeJavaVersion());
        return processorManager.processLibraries(libraries, libraryContext);
    }

    private JavaVersion getTargetRuntimeJavaVersion() {
        final Object property = GradleUtils.getProperty(project,
            Constants.Properties.RUNTIME_JAVA_COMPATIBILITY_VERSION);

        if (property != null) {
            // This is very much a last ditch effort to allow users to set the runtime java version
            // It's not recommended and will likely cause support confusion if it has been changed without good reason.
            project.getLogger()
                .warn("Runtime java compatibility version has manually been set to: %s".formatted(property));
            return JavaVersion.toVersion(property);
        }

        return JavaVersion.current();
    }

    private void applyClientLibrary(Library library) {
        switch (library.target()) {
            case COMPILE -> addLibrary(Constants.Configurations.ZOMBOID_CLIENT_COMPILE_LIBRARIES, library);
            case RUNTIME -> addLibrary(Constants.Configurations.ZOMBOID_CLIENT_RUNTIME_LIBRARIES, library);
            case NATIVES -> addLibrary(Constants.Configurations.ZOMBOID_NATIVES, library);
            case LOCAL_MOD -> applyLocalModLibrary(library);
        }
    }

    private void applyServerLibrary(Library library) {
        switch (library.target()) {
            case COMPILE -> addLibrary(Constants.Configurations.ZOMBOID_SERVER_COMPILE_LIBRARIES, library);
            case RUNTIME -> addLibrary(Constants.Configurations.ZOMBOID_SERVER_RUNTIME_LIBRARIES, library);
            case LOCAL_MOD -> applyLocalModLibrary(library);
            default -> throw new IllegalStateException(
                "Target not supported for server library: %s".formatted(library));
        }
    }

    private void applyLocalModLibrary(Library library) {
        ExternalModuleDependency dependency =
            (ExternalModuleDependency) project.getDependencies().create(library.mavenNotation());
        dependency.setTransitive(false);
        project.getDependencies().add("modLocalRuntime", dependency);
    }

    private void addLibrary(String configuration, Library library) {
        addDependency(configuration, library.mavenNotation());
    }

    private void addDependency(String configuration, Object dependency) {
        final Dependency created = project.getDependencies().add(configuration, dependency);

        // The launcher doesn't download transitive deps, so neither will we.
        // This will also prevent a LaunchWrapper library dependency from pulling in outdated ASM jars.
        if (created instanceof ModuleDependency md) {
            md.setTransitive(false);
        }
    }
}

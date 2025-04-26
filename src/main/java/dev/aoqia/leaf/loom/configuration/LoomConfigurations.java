/*
 * This file is part of leaf-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2023 aoqia, FabricMC
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

import java.util.ArrayList;
import java.util.List;

import dev.aoqia.leaf.loom.LoomGradleExtension;
import dev.aoqia.leaf.loom.util.Constants;
import dev.aoqia.leaf.loom.util.LoomVersions;
import dev.aoqia.leaf.loom.util.gradle.SourceSetHelper;

import javax.inject.Inject;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.attributes.*;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Provider;

public abstract class LoomConfigurations implements Runnable {
    @Override
    public void run() {
        final LoomGradleExtension extension = LoomGradleExtension.get(getProject());

        register(Constants.Configurations.MOD_COMPILE_CLASSPATH, Role.RESOLVABLE);
        registerNonTransitive(Constants.Configurations.MOD_COMPILE_CLASSPATH_MAPPED,
            Role.RESOLVABLE);

        // Set up the Zomboid compile configurations.
        var zomboidClientCompile = registerNonTransitive(
            Constants.Configurations.ZOMBOID_CLIENT_COMPILE_LIBRARIES, Role.RESOLVABLE);
        var zomboidServerCompile = registerNonTransitive(
            Constants.Configurations.ZOMBOID_SERVER_COMPILE_LIBRARIES, Role.RESOLVABLE);
        var zomboidCompile = registerNonTransitive(
            Constants.Configurations.ZOMBOID_COMPILE_LIBRARIES, Role.RESOLVABLE);

        zomboidCompile.configure(configuration -> {
            configuration.extendsFrom(zomboidClientCompile.get());
            configuration.extendsFrom(zomboidServerCompile.get());
        });

        // Set up the zomboid runtime configurations, this extends from the compile configurations.
        var zomboidClientRuntime = registerNonTransitive(
            Constants.Configurations.ZOMBOID_CLIENT_RUNTIME_LIBRARIES, Role.RESOLVABLE);
        var zomboidServerRuntime = registerNonTransitive(
            Constants.Configurations.ZOMBOID_SERVER_RUNTIME_LIBRARIES, Role.RESOLVABLE);

        // Runtime extends from compile
        zomboidClientRuntime.configure(configuration -> {
            configuration.extendsFrom(zomboidClientCompile.get());
        });
        zomboidServerRuntime.configure(configuration -> {
            configuration.extendsFrom(zomboidServerCompile.get());
        });

        registerNonTransitive(Constants.Configurations.ZOMBOID_RUNTIME_LIBRARIES,
            Role.RESOLVABLE).configure(runtime -> {
            runtime.extendsFrom(zomboidClientRuntime.get());
            runtime.extendsFrom(zomboidServerRuntime.get());
        });

        registerNonTransitive(Constants.Configurations.ZOMBOID_NATIVES, Role.RESOLVABLE);
        registerNonTransitive(Constants.Configurations.ZOMBOID_EXTRACTED_LIBRARIES,
            Role.RESOLVABLE);

        registerNonTransitive(Constants.Configurations.LOADER_DEPENDENCIES, Role.RESOLVABLE);

        registerNonTransitive(Constants.Configurations.ZOMBOID, Role.NONE);

        Provider<Configuration> include = register(Constants.Configurations.INCLUDE, Role.NONE);
        register(Constants.Configurations.INCLUDE_INTERNAL, Role.RESOLVABLE).configure(
            configuration -> {
                configuration.getDependencies().addAllLater(getProject().provider(() -> {
                    List<Dependency> dependencies = new ArrayList<>();

                    for (Dependency dependency : include.get().getIncoming().getDependencies()) {
                        if (dependency instanceof HasConfigurableAttributes<?> hasAttributes) {
                            Category category = hasAttributes.getAttributes()
                                .getAttribute(Category.CATEGORY_ATTRIBUTE);

                            if (category != null &&
                                (category.getName().equals(Category.ENFORCED_PLATFORM) ||
                                 category.getName().equals(Category.REGULAR_PLATFORM))) {
                                dependencies.add(dependency);
                                continue;
                            } else if (dependency instanceof ModuleDependency moduleDependency) {
                                ModuleDependency copy = moduleDependency.copy();
                                copy.setTransitive(false);
                                dependencies.add(copy);
                                continue;
                            }
                        }

                        dependencies.add(dependency);
                    }

                    return dependencies;
                }));

                configuration.attributes(attributes -> {
                    attributes.attribute(Usage.USAGE_ATTRIBUTE,
                        getProject().getObjects().named(Usage.class, Usage.JAVA_RUNTIME));
                    attributes.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                        getProject().getObjects()
                            .named(LibraryElements.class, LibraryElements.JAR));
                    attributes.attribute(Category.CATEGORY_ATTRIBUTE,
                        getProject().getObjects().named(Category.class, Category.LIBRARY));
                    attributes.attribute(Bundling.BUNDLING_ATTRIBUTE,
                        getProject().getObjects().named(Bundling.class, Bundling.EXTERNAL));
                });
            });

        registerNonTransitive(Constants.Configurations.MAPPING_CONSTANTS, Role.RESOLVABLE);

        register(Constants.Configurations.NAMED_ELEMENTS, Role.CONSUMABLE).configure(
            configuration -> {
                configuration.extendsFrom(
                    getConfigurations().getByName(JavaPlugin.API_CONFIGURATION_NAME));
            });

        extendsFrom(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME,
            Constants.Configurations.MAPPING_CONSTANTS);

        register(Constants.Configurations.MAPPINGS, Role.RESOLVABLE);
        register(Constants.Configurations.MAPPINGS_FINAL, Role.RESOLVABLE);
        register(Constants.Configurations.LOOM_DEVELOPMENT_DEPENDENCIES, Role.RESOLVABLE);
        register(Constants.Configurations.UNPICK_CLASSPATH, Role.RESOLVABLE);
        register(Constants.Configurations.LOCAL_RUNTIME, Role.RESOLVABLE);
        extendsFrom(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME,
            Constants.Configurations.LOCAL_RUNTIME);

        extension.createRemapConfigurations(SourceSetHelper.getMainSourceSet(getProject()));

        extendsFrom(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME,
            Constants.Configurations.MAPPINGS_FINAL);
        extendsFrom(JavaPlugin.TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME,
            Constants.Configurations.MAPPINGS_FINAL);

        extendsFrom(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME,
            Constants.Configurations.ZOMBOID_RUNTIME_LIBRARIES);

        // Add the dev time dependencies
        getDependencies().add(Constants.Configurations.LOOM_DEVELOPMENT_DEPENDENCIES,
            LoomVersions.DEV_LAUNCH_INJECTOR.mavenNotation());
        getDependencies().add(Constants.Configurations.LOOM_DEVELOPMENT_DEPENDENCIES,
            LoomVersions.TERMINAL_CONSOLE_APPENDER.mavenNotation());
        getDependencies().add(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME,
            LoomVersions.JETBRAINS_ANNOTATIONS.mavenNotation());
        getDependencies().add(JavaPlugin.TEST_COMPILE_ONLY_CONFIGURATION_NAME,
            LoomVersions.JETBRAINS_ANNOTATIONS.mavenNotation());
    }

    @Inject
    protected abstract Project getProject();

    private NamedDomainObjectProvider<Configuration> register(String name, Role role) {
        return getConfigurations().register(name, role::apply);
    }

    private NamedDomainObjectProvider<Configuration> registerNonTransitive(String name, Role role) {
        final NamedDomainObjectProvider<Configuration> provider = register(name, role);
        provider.configure(configuration -> configuration.setTransitive(false));
        return provider;
    }

    @Inject
    protected abstract ConfigurationContainer getConfigurations();

    public void extendsFrom(String a, String b) {
        getConfigurations().getByName(a, configuration -> {
            configuration.extendsFrom(getConfigurations().getByName(b));
        });
    }

    @Inject
    protected abstract DependencyHandler getDependencies();

    enum Role {
        NONE(false, false),
        CONSUMABLE(true, false),
        RESOLVABLE(false, true);

        private final boolean canBeConsumed;
        private final boolean canBeResolved;

        Role(boolean canBeConsumed, boolean canBeResolved) {
            this.canBeConsumed = canBeConsumed;
            this.canBeResolved = canBeResolved;
        }

        void apply(Configuration configuration) {
            configuration.setCanBeConsumed(canBeConsumed);
            configuration.setCanBeResolved(canBeResolved);
        }
    }
}

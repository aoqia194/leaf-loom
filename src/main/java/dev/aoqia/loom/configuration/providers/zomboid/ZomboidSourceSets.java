/*
 * This file is part of leaf-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2022-2023 aoqia, FabricMC
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
package net.aoqia.loom.configuration.providers.zomboid;

import java.util.List;
import java.util.function.BiConsumer;

import com.google.common.base.Preconditions;
import net.aoqia.loom.LoomGradleExtension;
import net.aoqia.loom.configuration.RemapConfigurations;
import net.aoqia.loom.task.AbstractRemapJarTask;
import net.aoqia.loom.util.Constants;
import net.aoqia.loom.util.gradle.SourceSetHelper;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.SourceSet;
import org.gradle.jvm.tasks.Jar;

public abstract sealed class ZomboidSourceSets permits ZomboidSourceSets.Single, ZomboidSourceSets.Split {
    public static ZomboidSourceSets get(Project project) {
        return LoomGradleExtension.get(project).areEnvironmentSourceSetsSplit() ? Split.INSTANCE : Single.INSTANCE;
    }

    public abstract void applyDependencies(
        BiConsumer<String, ZomboidJar.Type> consumer, List<ZomboidJar.Type> targets);

    public abstract String getSourceSetForEnv(String env);

    public void evaluateSplit(Project project) {
        final LoomGradleExtension extension = LoomGradleExtension.get(project);
        Preconditions.checkArgument(extension.areEnvironmentSourceSetsSplit());

        Split.INSTANCE.evaluate(project);
    }

    public abstract void afterEvaluate(Project project);

    protected void createConfigurations(Project project) {
        final ConfigurationContainer configurations = project.getConfigurations();

        for (ConfigurationName configurationName : getConfigurations()) {
            configurations.register(configurationName.runtime(), configuration -> {
                configuration.setTransitive(false);
                configuration.extendsFrom(configurations.getByName(configurationName.zomboidLibsRuntimeName()));
                configuration.extendsFrom(configurations.getByName(Constants.Configurations.LOADER_DEPENDENCIES));
                configuration.extendsFrom(
                    configurations.getByName(Constants.Configurations.LOOM_DEVELOPMENT_DEPENDENCIES));
            });

            configurations.register(configurationName.compile(), configuration -> {
                configuration.setTransitive(false);
                configuration.extendsFrom(configurations.getByName(configurationName.zomboidLibsCompileName()));
                configuration.extendsFrom(configurations.getByName(Constants.Configurations.LOADER_DEPENDENCIES));
            });
        }
    }

    protected abstract List<ConfigurationName> getConfigurations();

    protected void extendsFrom(Project project, String name, String extendsFrom) {
        final ConfigurationContainer configurations = project.getConfigurations();

        configurations.named(name, configuration -> {
            configuration.extendsFrom(configurations.getByName(extendsFrom));
        });
    }

    /**
     * Used when we have a single source set, either with split or merged jars.
     */
    public static final class Single extends ZomboidSourceSets {
        private static final ConfigurationName ZOMBOID_NAMED = new ConfigurationName(
            "zomboidNamed",
            Constants.Configurations.ZOMBOID_COMPILE_LIBRARIES,
            Constants.Configurations.ZOMBOID_RUNTIME_LIBRARIES);

        private static final Single INSTANCE = new Single();

        @Override
        public void applyDependencies(BiConsumer<String, ZomboidJar.Type> consumer, List<ZomboidJar.Type> targets) {
            for (ZomboidJar.Type target : targets) {
                consumer.accept(ZOMBOID_NAMED.compile(), target);
                consumer.accept(ZOMBOID_NAMED.runtime(), target);
            }
        }

        @Override
        public String getSourceSetForEnv(String env) {
            return SourceSet.MAIN_SOURCE_SET_NAME;
        }

        @Override
        public void afterEvaluate(Project project) {
            // This is done in afterEvaluate as we need to be sure that split source sets was not enabled.
            createConfigurations(project);

            extendsFrom(project, JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME, ZOMBOID_NAMED.compile());
            extendsFrom(project, JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME, ZOMBOID_NAMED.runtime());
            extendsFrom(project, JavaPlugin.TEST_COMPILE_CLASSPATH_CONFIGURATION_NAME, ZOMBOID_NAMED.compile());
            extendsFrom(project, JavaPlugin.TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME, ZOMBOID_NAMED.runtime());
        }

        @Override
        protected List<ConfigurationName> getConfigurations() {
            return List.of(ZOMBOID_NAMED);
        }
    }

    /**
     * Used when we have a split client/common source set and split jars.
     */
    public static final class Split extends ZomboidSourceSets {
        public static final String CLIENT_ONLY_SOURCE_SET_NAME = "client";
        private static final ConfigurationName ZOMBOID_COMMON_NAMED = new ConfigurationName(
            "zomboidCommonNamed",
            Constants.Configurations.ZOMBOID_COMPILE_LIBRARIES,
            Constants.Configurations.ZOMBOID_RUNTIME_LIBRARIES);
        // Depends on the Zomboid client libraries.
        private static final ConfigurationName ZOMBOID_CLIENT_ONLY_NAMED = new ConfigurationName(
            "zomboidClientOnlyNamed",
            Constants.Configurations.ZOMBOID_CLIENT_COMPILE_LIBRARIES,
            Constants.Configurations.ZOMBOID_CLIENT_RUNTIME_LIBRARIES);
        private static final Split INSTANCE = new Split();

        @Override
        public void applyDependencies(BiConsumer<String, ZomboidJar.Type> consumer, List<ZomboidJar.Type> targets) {
            Preconditions.checkArgument(targets.size() == 2);
            Preconditions.checkArgument(targets.contains(ZomboidJar.Type.COMMON));
            Preconditions.checkArgument(targets.contains(ZomboidJar.Type.CLIENT_ONLY));

            consumer.accept(ZOMBOID_COMMON_NAMED.runtime(), ZomboidJar.Type.COMMON);
            consumer.accept(ZOMBOID_CLIENT_ONLY_NAMED.runtime(), ZomboidJar.Type.CLIENT_ONLY);
            consumer.accept(ZOMBOID_COMMON_NAMED.compile(), ZomboidJar.Type.COMMON);
            consumer.accept(ZOMBOID_CLIENT_ONLY_NAMED.compile(), ZomboidJar.Type.CLIENT_ONLY);
        }

        @Override
        public String getSourceSetForEnv(String env) {
            return env.equals("client") ? CLIENT_ONLY_SOURCE_SET_NAME : SourceSet.MAIN_SOURCE_SET_NAME;
        }

        @Override
        public void afterEvaluate(Project project) {
        }

        @Override
        protected List<ConfigurationName> getConfigurations() {
            return List.of(ZOMBOID_COMMON_NAMED, ZOMBOID_CLIENT_ONLY_NAMED);
        }

        // Called during evaluation, when the loom extension method is called.
        private void evaluate(Project project) {
            createConfigurations(project);
            final ConfigurationContainer configurations = project.getConfigurations();
            final LoomGradleExtension extension = LoomGradleExtension.get(project);

            // Register our new client only source set, main becomes common only, with their respective jars.
            final SourceSet mainSourceSet = SourceSetHelper.getMainSourceSet(project);
            final SourceSet clientOnlySourceSet = SourceSetHelper.createSourceSet(CLIENT_ONLY_SOURCE_SET_NAME, project);

            // Add Zomboid to the main and client source sets.
            extendsFrom(project, mainSourceSet.getCompileClasspathConfigurationName(), ZOMBOID_COMMON_NAMED.compile());
            extendsFrom(project, mainSourceSet.getRuntimeClasspathConfigurationName(), ZOMBOID_COMMON_NAMED.runtime());
            extendsFrom(project, clientOnlySourceSet.getCompileClasspathConfigurationName(),
                ZOMBOID_CLIENT_ONLY_NAMED.compile());
            extendsFrom(project, clientOnlySourceSet.getRuntimeClasspathConfigurationName(),
                ZOMBOID_CLIENT_ONLY_NAMED.runtime());

            // Client source set depends on common.
            extendsFrom(project, ZOMBOID_CLIENT_ONLY_NAMED.runtime(), ZOMBOID_COMMON_NAMED.runtime());
            extendsFrom(project, ZOMBOID_CLIENT_ONLY_NAMED.compile(), ZOMBOID_COMMON_NAMED.compile());

            // Client annotation processor configuration extendsFrom "annotationProcessor"
            extendsFrom(
                project,
                clientOnlySourceSet.getAnnotationProcessorConfigurationName(),
                JavaPlugin.ANNOTATION_PROCESSOR_CONFIGURATION_NAME);

            // Client source set classpaths
            clientOnlySourceSet.setCompileClasspath(clientOnlySourceSet
                .getCompileClasspath().plus(mainSourceSet.getOutput()));
            clientOnlySourceSet.setRuntimeClasspath(clientOnlySourceSet
                .getRuntimeClasspath().plus(mainSourceSet.getOutput()));

            extendsFrom(
                project,
                clientOnlySourceSet.getCompileClasspathConfigurationName(),
                mainSourceSet.getCompileClasspathConfigurationName());
            extendsFrom(
                project,
                clientOnlySourceSet.getRuntimeClasspathConfigurationName(),
                mainSourceSet.getRuntimeClasspathConfigurationName());

            // Test source set depends on client
            final SourceSet testSourceSet = SourceSetHelper.getSourceSetByName(SourceSet.TEST_SOURCE_SET_NAME, project);
            extendsFrom(
                project,
                testSourceSet.getCompileClasspathConfigurationName(),
                clientOnlySourceSet.getCompileClasspathConfigurationName());
            extendsFrom(
                project,
                testSourceSet.getRuntimeClasspathConfigurationName(),
                clientOnlySourceSet.getRuntimeClasspathConfigurationName());
            project.getDependencies()
                .add(testSourceSet.getImplementationConfigurationName(), clientOnlySourceSet.getOutput());

            RemapConfigurations.configureClientConfigurations(project, clientOnlySourceSet);

            // Include the client only output in the jars
            project.getTasks().named(mainSourceSet.getJarTaskName(), Jar.class).configure(jar -> {
                jar.from(clientOnlySourceSet.getOutput().getClassesDirs());
                jar.from(clientOnlySourceSet.getOutput().getResourcesDir());

                jar.dependsOn(project.getTasks().named(clientOnlySourceSet.getProcessResourcesTaskName()));
            });

            // Remap with the client compile classpath.
            project.getTasks().withType(AbstractRemapJarTask.class).configureEach(remapJarTask -> {
                remapJarTask
                    .getClasspath()
                    .from(project.getConfigurations()
                        .getByName(clientOnlySourceSet.getCompileClasspathConfigurationName()));
            });

            // The sources task can be registered at a later time.
            project.getTasks().configureEach(task -> {
                if (!mainSourceSet.getSourcesJarTaskName().equals(task.getName()) || !(task instanceof Jar jar)) {
                    // Not the sources task we are looking for.
                    return;
                }

                // The client only sources to the combined sources jar.
                jar.from(clientOnlySourceSet.getAllSource());
            });

            project.getTasks().withType(AbstractRemapJarTask.class, task -> {
                // Set the default client only source set name
                task.getClientOnlySourceSetName().convention(CLIENT_ONLY_SOURCE_SET_NAME);
            });
        }
    }

    private record ConfigurationName(String baseName, String zomboidLibsCompileName, String zomboidLibsRuntimeName) {
        private String runtime() {
            return baseName + "Runtime";
        }

        private String compile() {
            return baseName + "Compile";
        }
    }
}

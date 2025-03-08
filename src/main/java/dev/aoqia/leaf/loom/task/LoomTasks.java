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
package dev.aoqia.leaf.loom.task;

import javax.inject.Inject;

import com.google.common.base.Preconditions;
import dev.aoqia.leaf.loom.LoomGradleExtension;
import dev.aoqia.leaf.loom.configuration.ide.RunConfigSettings;
import dev.aoqia.leaf.loom.configuration.providers.zomboid.ZomboidJarConfiguration;
import dev.aoqia.leaf.loom.configuration.providers.zomboid.ZomboidVersionMeta;
import dev.aoqia.leaf.loom.task.launch.GenerateDLIConfigTask;
import dev.aoqia.leaf.loom.task.launch.GenerateLog4jConfigTask;
import dev.aoqia.leaf.loom.task.launch.GenerateRemapClasspathTask;
import dev.aoqia.leaf.loom.util.Constants;
import dev.aoqia.leaf.loom.util.gradle.GradleUtils;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;

public abstract class LoomTasks implements Runnable {
    @Override
    public void run() {
        // If only provide jars then dont setup tasks or anything else.
        if (GradleUtils.getBooleanProperty(getProject(), Constants.Properties.ONLY_PROVIDE_JARS)) {
            return;
        }

        var generateLog4jConfig = getTasks().register("generateLog4jConfig", GenerateLog4jConfigTask.class, t -> {
            t.setDescription("Generate the log4j config file");
        });
        var generateRemapClasspath = getTasks()
            .register("generateRemapClasspath", GenerateRemapClasspathTask.class, t -> {
                t.setDescription("Generate the remap classpath file");
            });
        getTasks().register("generateDLIConfig", GenerateDLIConfigTask.class, t -> {
            t.setDescription("Generate the DevLaunchInjector config file");

            // Must allow these IDE files to be generated first
            t.mustRunAfter("eclipse");

            t.dependsOn(generateLog4jConfig);
            t.getRemapClasspathFile().set(generateRemapClasspath.get().getRemapClasspathFile());
        });

        getTasks().register("configureLaunch", task -> {
            task.dependsOn(getTasks().named("generateDLIConfig"));
            task.dependsOn(getTasks().named("generateLog4jConfig"));
            task.dependsOn(getTasks().named("generateRemapClasspath"));

            task.setDescription("Setup the required files to launch Zomboid");
            task.setGroup(Constants.TaskGroup.LEAF);
        });

        TaskProvider<ValidateAccessWidenerTask> validateAccessWidener = getTasks()
            .register("validateAccessWidener", ValidateAccessWidenerTask.class, t -> {
                t.setDescription("Validate all the rules in the access widener against the Zomboid jar");
                t.setGroup("verification");
            });

        getTasks().named("check").configure(task -> task.dependsOn(validateAccessWidener));

        registerIDETasks();
        registerRunTasks();

        // Must be done in afterEvaluate to allow time for the build script to configure the jar config.
        GradleUtils.afterSuccessfulEvaluation(getProject(), () -> {
            LoomGradleExtension extension = LoomGradleExtension.get(getProject());

            if (extension.getZomboidJarConfiguration().get() == ZomboidJarConfiguration.SERVER_ONLY) {
                // Server only, nothing more to do.
                //                final ZomboidVersionMeta serverVersionInfo = extension.getZomboidProvider()
                //                .getServerVersionInfo();
                //                registerServerSetupTasks(getTasks(), serverVersionInfo.hasNativesToExtract());
                return;
            }

            final ZomboidVersionMeta versionInfo = extension.getZomboidProvider().getClientVersionInfo();
            if (versionInfo == null) {
                // Something has gone wrong, don't register the task.
                System.out.println("LoomTasks: Version info is null.");
                return;
            }

            registerClientSetupTasks(getTasks(), versionInfo.hasNativesToExtract());
        });
    }

    @Inject
    protected abstract Project getProject();

    @Inject
    protected abstract TaskContainer getTasks();

    private void registerIDETasks() {
        getTasks().register("genEclipseRuns", GenEclipseRunsTask.class, t -> {
            t.setDescription("Generates Eclipse run configurations for this project.");
            t.dependsOn(getIDELaunchConfigureTaskName(getProject()));
            t.setGroup(Constants.TaskGroup.IDE);
        });

        getTasks().register("vscode", GenVsCodeProjectTask.class, t -> {
            t.setDescription("Generates VSCode launch configurations.");
            t.dependsOn(getIDELaunchConfigureTaskName(getProject()));
            t.setGroup(Constants.TaskGroup.IDE);
        });
    }

    private void registerRunTasks() {
        LoomGradleExtension extension = LoomGradleExtension.get(getProject());

        Preconditions.checkArgument(
            extension.getRunConfigs().isEmpty(),
            "Run configurations must not be registered before loom");

        extension.getRunConfigs().whenObjectAdded(config -> {
            getTasks()
                .register(getRunConfigTaskName(config), RunGameTask.class, config)
                .configure(t -> {
                    t.setDescription("Starts the '" + config.getConfigName() + "' run configuration");

                    t.dependsOn(config.getEnvironment().equals("client")
                        ? "configureClientLaunch" : "configureLaunch");
                });
        });

        extension.getRunConfigs().whenObjectRemoved(runConfigSettings -> {
            getTasks().named(getRunConfigTaskName(runConfigSettings), task -> {
                // Disable the task so it can't be run
                task.setEnabled(false);
            });
        });

        extension.getRunConfigs().create("client", RunConfigSettings::client);
        extension.getRunConfigs().create("server", RunConfigSettings::server);

        // Remove the client or server run config when not required. Done by name to not remove any possible custom run
        // configs
        GradleUtils.afterSuccessfulEvaluation(getProject(), () -> {
            String taskName;

            boolean serverOnly = extension.getZomboidJarConfiguration().get() == ZomboidJarConfiguration.SERVER_ONLY;
            boolean clientOnly = extension.getZomboidJarConfiguration().get() == ZomboidJarConfiguration.CLIENT_ONLY;

            if (serverOnly) {
                // Server only, remove the client run config
                taskName = "client";
            } else if (clientOnly) {
                // Client only, remove the server run config
                taskName = "server";
            } else {
                return;
            }

            extension.getRunConfigs().removeIf(settings -> settings.getName().equals(taskName));
        });
    }

    private static void registerClientSetupTasks(TaskContainer tasks, boolean extractNatives) {
        if (extractNatives) {
            tasks.register("extractNatives", ExtractNativesTask.class, t -> {
                t.setDescription("Extracts the platform specific natives.");
            });
        }

        tasks.register("configureClientLaunch", task -> {
            // task.dependsOn(tasks.named("copyClientAssets"));
            task.dependsOn(tasks.named("configureLaunch"));

            if (extractNatives) {
                task.dependsOn(tasks.named("extractNatives"));
            }

            task.setDescription("Setup the required files to launch the Zomboid client");
            task.setGroup(Constants.TaskGroup.LEAF);
        });
    }

    public static Provider<Task> getIDELaunchConfigureTaskName(Project project) {
        return project.provider(() -> {
            final ZomboidJarConfiguration jarConfiguration = LoomGradleExtension.get(project)
                .getZomboidJarConfiguration()
                .get();
            final String name = jarConfiguration == ZomboidJarConfiguration.SERVER_ONLY
                ? "configureLaunch"
                : "configureClientLaunch";
            return project.getTasks().getByName(name);
        });
    }

    private static String getRunConfigTaskName(RunConfigSettings config) {
        String configName = config.getName();
        return "run" + configName.substring(0, 1).toUpperCase() + configName.substring(1);
    }
}

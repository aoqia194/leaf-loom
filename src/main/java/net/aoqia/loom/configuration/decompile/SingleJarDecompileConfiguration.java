/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2022 FabricMC
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

package net.aoqia.loom.configuration.decompile;

import java.io.File;
import java.util.List;
import net.aoqia.loom.LoomGradleExtension;
import net.aoqia.loom.configuration.providers.zomboid.ZomboidJar;
import net.aoqia.loom.configuration.providers.zomboid.mapped.MappedZomboidProvider;
import net.aoqia.loom.task.GenerateSourcesTask;
import net.aoqia.loom.util.Constants;
import org.gradle.api.Project;

public class SingleJarDecompileConfiguration extends DecompileConfiguration<MappedZomboidProvider> {
    public SingleJarDecompileConfiguration(Project project, MappedZomboidProvider minecraftProvider) {
        super(project, minecraftProvider);
    }

    @Override
    public String getTaskName(ZomboidJar.Type type) {
        return "genSources";
    }

    @Override
    public final void afterEvaluation() {
        final List<ZomboidJar> zomboidJars = minecraftProvider.getMinecraftJars();
        assert zomboidJars.size() == 1;
        final ZomboidJar zomboidJar = zomboidJars.get(0);
        final String taskBaseName = getTaskName(zomboidJar.getType());

        LoomGradleExtension.get(project).getDecompilerOptions().forEach(options -> {
            final String decompilerName = options.getFormattedName();
            String taskName = "%sWith%s".formatted(taskBaseName, decompilerName);
            // Decompiler will be passed to the constructor of GenerateSourcesTask
            project.getTasks()
                    .register(taskName, GenerateSourcesTask.class, options)
                    .configure(task -> {
                        task.getInputJarName().set(zomboidJar.getName());
                        task.getSourcesOutputJar()
                                .fileValue(GenerateSourcesTask.getJarFileWithSuffix(
                                        "-sources.jar", zomboidJar.getPath()));

                        task.dependsOn(project.getTasks().named("validateAccessWidener"));
                        task.setDescription("Decompile zomboid using %s.".formatted(decompilerName));
                        task.setGroup(Constants.TaskGroup.LEAF);

                        if (mappingConfiguration.hasUnpickDefinitions()) {
                            final File outputJar = new File(
                                    extension
                                            .getMappingConfiguration()
                                            .mappingsWorkingDir()
                                            .toFile(),
                                    "zomboid-unpicked.jar");
                            configureUnpick(task, outputJar);
                        }
                    });
        });

        project.getTasks().register(taskBaseName, task -> {
            task.setDescription("Decompile zomboid using the default decompiler.");
            task.setGroup(Constants.TaskGroup.LEAF);

            task.dependsOn(project.getTasks().named("genSourcesWith" + DecompileConfiguration.DEFAULT_DECOMPILER));
        });
    }
}

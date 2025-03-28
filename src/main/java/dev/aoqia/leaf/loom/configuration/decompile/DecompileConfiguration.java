/*
 * This file is part of leaf-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2022 aoqia, FabricMC
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
package dev.aoqia.leaf.loom.configuration.decompile;

import java.io.File;
import dev.aoqia.leaf.loom.LoomGradleExtension;
import dev.aoqia.leaf.loom.api.mappings.layered.MappingsNamespace;
import dev.aoqia.leaf.loom.configuration.providers.mappings.MappingConfiguration;
import dev.aoqia.leaf.loom.configuration.providers.zomboid.ZomboidJar;
import dev.aoqia.leaf.loom.configuration.providers.zomboid.mapped.MappedZomboidProvider;
import dev.aoqia.leaf.loom.task.GenerateSourcesTask;
import dev.aoqia.leaf.loom.util.Constants;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ConfigurationContainer;

public abstract class DecompileConfiguration<T extends MappedZomboidProvider> {
    static final String DEFAULT_DECOMPILER = "Vineflower";

    protected final Project project;
    protected final T minecraftProvider;
    protected final LoomGradleExtension extension;
    protected final MappingConfiguration mappingConfiguration;

    public DecompileConfiguration(Project project, T minecraftProvider) {
        this.project = project;
        this.minecraftProvider = minecraftProvider;
        this.extension = LoomGradleExtension.get(project);
        this.mappingConfiguration = extension.getMappingConfiguration();
    }

    public abstract String getTaskName(ZomboidJar.Type type);

    public abstract void afterEvaluation();

    protected final void configureUnpick(GenerateSourcesTask task, File unpickOutputJar) {
        final ConfigurationContainer configurations = task.getProject().getConfigurations();

        task.getUnpickDefinitions().set(mappingConfiguration.getUnpickDefinitionsFile());
        task.getUnpickOutputJar().set(unpickOutputJar);
        task.getUnpickConstantJar().setFrom(configurations.getByName(Constants.Configurations.MAPPING_CONSTANTS));
        task.getUnpickClasspath().setFrom(configurations.getByName(Constants.Configurations.ZOMBOID_COMPILE_LIBRARIES));
        task.getUnpickClasspath().from(configurations.getByName(Constants.Configurations.MOD_COMPILE_CLASSPATH_MAPPED));
        extension.getZomboidJars(MappingsNamespace.NAMED).forEach(task.getUnpickClasspath()::from);
    }
}

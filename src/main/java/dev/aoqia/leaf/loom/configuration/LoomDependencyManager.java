/*
 * This file is part of leaf-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016-2021 aoqia, FabricMC
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

import dev.aoqia.leaf.loom.LoomGradleExtension;
import dev.aoqia.leaf.loom.configuration.mods.ModConfigurationRemapper;
import dev.aoqia.leaf.loom.util.SourceRemapper;
import dev.aoqia.leaf.loom.util.service.ServiceFactory;
import org.gradle.api.Project;

public class LoomDependencyManager {
    public void handleDependencies(Project project, ServiceFactory serviceFactory) {
        project.getLogger().info(":setting up loom dependencies");
        LoomGradleExtension extension = LoomGradleExtension.get(project);

        SourceRemapper sourceRemapper = new SourceRemapper(project, serviceFactory, true);
        String mappingsIdentifier = extension.getMappingConfiguration().mappingsIdentifier();

        ModConfigurationRemapper.supplyModConfigurations(
                project, serviceFactory, mappingsIdentifier, extension, sourceRemapper);

        sourceRemapper.remapAll();

        if (extension.getInstallerData() == null) {
            project.getLogger().info("leaf-installer.json not found in dependencies");
        }
    }
}

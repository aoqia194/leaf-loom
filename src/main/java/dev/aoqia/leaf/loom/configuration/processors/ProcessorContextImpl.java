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
package dev.aoqia.leaf.loom.configuration.processors;

import dev.aoqia.leaf.loom.LoomGradleExtension;
import dev.aoqia.leaf.loom.api.mappings.layered.MappingsNamespace;
import dev.aoqia.leaf.loom.api.processor.ProcessorContext;
import dev.aoqia.leaf.loom.configuration.ConfigContext;
import dev.aoqia.leaf.loom.configuration.providers.zomboid.ZomboidJar;
import dev.aoqia.leaf.loom.configuration.providers.zomboid.ZomboidJarConfiguration;
import dev.aoqia.leaf.loom.util.LazyCloseable;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.fabricmc.tinyremapper.TinyRemapper;

public record ProcessorContextImpl(ConfigContext configContext, ZomboidJar zomboidJar) implements ProcessorContext {
    @Override
    public ZomboidJarConfiguration getJarConfiguration() {
        return configContext.extension().getZomboidJarConfiguration().get();
    }

    @Override
    public boolean isMerged() {
        return zomboidJar.isMerged();
    }

    @Override
    public boolean includesClient() {
        return zomboidJar.includesClient();
    }

    @Override
    public boolean includesServer() {
        return zomboidJar.includesServer();
    }

    @Override
    public LazyCloseable<TinyRemapper> createRemapper(MappingsNamespace from, MappingsNamespace to) {
        return ContextImplHelper.createRemapper(configContext, from, to);
    }

    @Override
    public MemoryMappingTree getMappings() {
        LoomGradleExtension extension = LoomGradleExtension.get(configContext().project());
        return extension
                .getMappingConfiguration()
                .getMappingsService(configContext().project(), configContext().serviceFactory())
                .getMappingTree();
    }
}

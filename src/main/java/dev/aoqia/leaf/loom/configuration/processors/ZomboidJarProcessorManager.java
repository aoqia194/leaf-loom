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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import dev.aoqia.leaf.loom.LoomGradleExtension;
import dev.aoqia.leaf.loom.api.processor.MappingProcessorContext;
import dev.aoqia.leaf.loom.api.processor.ProcessorContext;
import dev.aoqia.leaf.loom.api.processor.SpecContext;
import dev.aoqia.leaf.loom.api.processor.ZomboidJarProcessor;
import dev.aoqia.leaf.loom.util.Checksum;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import org.gradle.api.Project;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ZomboidJarProcessorManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ZomboidJarProcessorManager.class);

    private final List<ProcessorEntry<?>> jarProcessors;

    private ZomboidJarProcessorManager(List<ProcessorEntry<?>> jarProcessors) {
        this.jarProcessors = Collections.unmodifiableList(jarProcessors);
    }

    @Nullable
    public static ZomboidJarProcessorManager create(Project project) {
        final LoomGradleExtension extension = LoomGradleExtension.get(project);
        List<ZomboidJarProcessor<?>> processors = new ArrayList<>(
            extension.getZomboidJarProcessors().get());

        for (JarProcessor legacyProcessor : extension.getGameJarProcessors().get()) {
            processors.add(
                project.getObjects().newInstance(LegacyJarProcessorWrapper.class, legacyProcessor));
        }

        return ZomboidJarProcessorManager.create(processors, SpecContextImpl.create(project));
    }

    @Nullable
    public static ZomboidJarProcessorManager create(List<ZomboidJarProcessor<?>> processors,
        SpecContext context) {
        List<ProcessorEntry<?>> entries = new ArrayList<>();

        for (ZomboidJarProcessor<?> processor : processors) {
            LOGGER.debug("Building processor spec for {}", processor.getName());
            ZomboidJarProcessor.Spec spec = processor.buildSpec(context);

            if (spec != null) {
                LOGGER.debug("Adding processor entry for {}", processor.getName());
                entries.add(new ProcessorEntry<>(processor, spec));
            }
        }

        if (entries.isEmpty()) {
            LOGGER.debug("No processor entries");
            return null;
        }

        return new ZomboidJarProcessorManager(entries);
    }

    public String getSourceMappingsHash() {
        return Checksum.sha1Hex(getCacheValue().getBytes(StandardCharsets.UTF_8));
    }

    private String getCacheValue() {
        return jarProcessors.stream()
            .sorted(Comparator.comparing(ProcessorEntry::name))
            .map(ProcessorEntry::cacheValue)
            .collect(Collectors.joining("::"));
    }

    private String getDebugString() {
        final var sj = new StringJoiner("\n");

        for (ProcessorEntry<?> jarProcessor : jarProcessors) {
            sj.add(jarProcessor.name() + ":");
            sj.add("\tHash: " + jarProcessor.hashCode());
            sj.add("\tStr: " + jarProcessor.cacheValue());
        }

        return sj.toString();
    }

    public String getJarHash() {
        // fabric-loom:mod-javadoc:-1289977000
        return Checksum.sha1Hex(getCacheValue().getBytes(StandardCharsets.UTF_8))
            .substring(0, 10);
    }

    public boolean requiresProcessingJar(Path jar) {
        Objects.requireNonNull(jar);

        if (Files.notExists(jar)) {
            LOGGER.debug("{} does not exist, generating", jar);
            return true;
        }

        return false;
    }

    public void processJar(Path jar, ProcessorContext context) throws IOException {
        for (ProcessorEntry<?> entry : jarProcessors) {
            try {
                entry.processJar(jar, context);
            } catch (IOException e) {
                throw new IOException(
                    "Failed to process jar when running jar processor: %s".formatted(entry.name()),
                    e);
            }
        }
    }

    public boolean processMappings(MemoryMappingTree mappings, MappingProcessorContext context) {
        boolean transformed = false;

        for (ProcessorEntry<?> entry : jarProcessors) {
            if (entry.processMappings(mappings, context)) {
                transformed = true;
            }
        }

        return transformed;
    }

    record ProcessorEntry<S extends ZomboidJarProcessor.Spec>(
        S spec,
        ZomboidJarProcessor<S> processor,
        @Nullable ZomboidJarProcessor.MappingsProcessor<S> mappingsProcessor) {
        @SuppressWarnings("unchecked")
        ProcessorEntry(ZomboidJarProcessor<?> processor, ZomboidJarProcessor.Spec spec) {
            this(
                (S) Objects.requireNonNull(spec),
                (ZomboidJarProcessor<S>) processor,
                (ZomboidJarProcessor.MappingsProcessor<S>) processor.processMappings());
        }

        private void processJar(Path jar, ProcessorContext context) throws IOException {
            processor().processJar(jar, spec, context);
        }

        private boolean processMappings(MemoryMappingTree mappings,
            MappingProcessorContext context) {
            if (mappingsProcessor() == null) {
                return false;
            }

            return mappingsProcessor().transform(mappings, spec, context);
        }

        private String name() {
            return processor.getName();
        }

        private String cacheValue() {
            return processor.getName() + ":" + spec.hashCode();
        }
    }
}

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
package dev.aoqia.leaf.loom.configuration.accesswidener;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.inject.Inject;
import net.fabricmc.accesswidener.AccessWidener;
import dev.aoqia.leaf.loom.api.mappings.layered.MappingsNamespace;
import dev.aoqia.leaf.loom.api.processor.ZomboidJarProcessor;
import dev.aoqia.leaf.loom.api.processor.ProcessorContext;
import dev.aoqia.leaf.loom.api.processor.SpecContext;
import dev.aoqia.leaf.loom.util.LazyCloseable;
import dev.aoqia.leaf.loom.util.fmj.LeafModJson;
import dev.aoqia.leaf.loom.util.fmj.ModEnvironment;
import net.fabricmc.tinyremapper.TinyRemapper;
import org.gradle.api.file.RegularFileProperty;
import org.jetbrains.annotations.Nullable;

public class AccessWidenerJarProcessor implements ZomboidJarProcessor<AccessWidenerJarProcessor.Spec> {
    private final String name;
    private final boolean includeTransitive;
    private final RegularFileProperty localAccessWidenerProperty;

    @Inject
    public AccessWidenerJarProcessor(
            String name, boolean includeTransitive, RegularFileProperty localAccessWidenerProperty) {
        this.name = name;
        this.includeTransitive = includeTransitive;
        this.localAccessWidenerProperty = localAccessWidenerProperty;
    }

    @Override
    public @Nullable AccessWidenerJarProcessor.Spec buildSpec(SpecContext context) {
        List<AccessWidenerEntry> accessWideners = new ArrayList<>();

        if (localAccessWidenerProperty.isPresent()) {
            Path path = localAccessWidenerProperty.get().getAsFile().toPath();

            if (Files.notExists(path)) {
                throw new UncheckedIOException(
                        new FileNotFoundException("Could not find access widener file at {%s}".formatted(path)));
            }

            // Add the access widener specified in the extension
            accessWideners.add(LocalAccessWidenerEntry.create(path));
        }

        /* Uncomment to read all access wideners from local mods.

        for (LeafModJson fabricModJson : context.localMods()) {
            accessWideners.addAll(ModAccessWidenerEntry.readAll(fabricModJson, false));
        }

         */

        if (includeTransitive) {
            for (LeafModJson leafModJson : context.modDependencies()) {
                accessWideners.addAll(ModAccessWidenerEntry.readAll(leafModJson, true));
            }
        }

        if (accessWideners.isEmpty()) {
            return null;
        }

        return new Spec(accessWideners.stream()
                .sorted(Comparator.comparing(AccessWidenerEntry::getSortKey))
                .toList());
    }

    @Override
    public String getName() {
        return name;
    }

    public record Spec(List<AccessWidenerEntry> accessWideners) implements ZomboidJarProcessor.Spec {
        List<AccessWidenerEntry> accessWidenersForContext(ProcessorContext context) {
            return accessWideners.stream()
                    .filter(entry -> isSupported(entry.environment(), context))
                    .toList();
        }

        private static boolean isSupported(ModEnvironment modEnvironment, ProcessorContext context) {
            if (context.isMerged()) {
                // All envs are supported wth a merged jar
                return true;
            }

            if (context.includesClient() && modEnvironment.isClient()) {
                return true;
            }

            if (context.includesServer() && modEnvironment.isServer()) {
                return true;
            }

            // Universal supports all jars
            return modEnvironment == ModEnvironment.UNIVERSAL;
        }
    }

    @Override
    public void processJar(Path jar, AccessWidenerJarProcessor.Spec spec, ProcessorContext context) throws IOException {
        final List<AccessWidenerEntry> accessWideners = spec.accessWidenersForContext(context);

        final var accessWidener = new AccessWidener();

        try (LazyCloseable<TinyRemapper> remapper =
                context.createRemapper(MappingsNamespace.OFFICIAL, MappingsNamespace.NAMED)) {
            for (AccessWidenerEntry widener : accessWideners) {
                widener.read(accessWidener, remapper);
            }
        }

        AccessWidenerTransformer transformer = new AccessWidenerTransformer(accessWidener);
        transformer.apply(jar);
    }

    @Override
    public @Nullable MappingsProcessor<Spec> processMappings() {
        return TransitiveAccessWidenerMappingsProcessor.INSTANCE;
    }
}

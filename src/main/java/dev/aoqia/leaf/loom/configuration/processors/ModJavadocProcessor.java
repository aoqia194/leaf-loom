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
package dev.aoqia.leaf.loom.configuration.processors;

import com.google.gson.JsonElement;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import javax.inject.Inject;
import dev.aoqia.leaf.loom.api.mappings.layered.MappingsNamespace;
import dev.aoqia.leaf.loom.api.processor.ZomboidJarProcessor;
import dev.aoqia.leaf.loom.api.processor.ProcessorContext;
import dev.aoqia.leaf.loom.api.processor.SpecContext;
import dev.aoqia.leaf.loom.util.Checksum;
import dev.aoqia.leaf.loom.util.Constants;
import dev.aoqia.leaf.loom.util.fmj.LeafModJson;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ModJavadocProcessor implements ZomboidJarProcessor<ModJavadocProcessor.Spec> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModJavadocProcessor.class);

    private final String name;

    @Inject
    public ModJavadocProcessor(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public @Nullable ModJavadocProcessor.Spec buildSpec(SpecContext context) {
        List<ModJavadoc> javadocs = new ArrayList<>();

        for (LeafModJson leafModJson : context.allMods()) {
            ModJavadoc javadoc = ModJavadoc.create(leafModJson);

            if (javadoc != null) {
                javadocs.add(javadoc);
            }
        }

        if (javadocs.isEmpty()) {
            return null;
        }

        javadocs.sort(Comparator.comparing(ModJavadoc::modId));
        return new Spec(Collections.unmodifiableList(javadocs));
    }

    public record Spec(List<ModJavadoc> javadocs) implements ZomboidJarProcessor.Spec {}

    @Override
    public void processJar(Path jar, Spec spec, ProcessorContext context) {
        // Nothing to do for the jar
    }

    @Override
    public @Nullable MappingsProcessor<Spec> processMappings() {
        return (mappings, spec, context) -> {
            for (ModJavadoc javadoc : spec.javadocs()) {
                javadoc.apply(mappings);
            }

            return true;
        };
    }

    public record ModJavadoc(String modId, MemoryMappingTree mappingTree, String mappingsHash) {
        @Nullable
        public static ModJavadoc create(LeafModJson leafModJson) {
            final String modId = leafModJson.getId();
            final JsonElement customElement = leafModJson.getCustom(Constants.CustomModJsonKeys.PROVIDED_JAVADOC);

            if (customElement == null) {
                return null;
            }

            final String javaDocPath = customElement.getAsString();
            final MemoryMappingTree mappings = new MemoryMappingTree();
            final String mappingsHash;

            try {
                final byte[] data = leafModJson.getSource().read(javaDocPath);
                mappingsHash = Checksum.sha1Hex(data);

                try (Reader reader = new InputStreamReader(new ByteArrayInputStream(data))) {
                    MappingReader.read(reader, mappings);
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to read javadoc from mod: " + modId, e);
            }

            if (!mappings.getSrcNamespace().equals(MappingsNamespace.OFFICIAL.toString())) {
                throw new IllegalStateException(
                        "Javadoc provided by mod (%s) must be have an intermediary source namespace".formatted(modId));
            }

            if (!mappings.getDstNamespaces().isEmpty()) {
                throw new IllegalStateException(
                        "Javadoc provided by mod (%s) must not contain any dst names".formatted(modId));
            }

            return new ModJavadoc(modId, mappings, mappingsHash);
        }

        public void apply(MemoryMappingTree target) {
            if (!mappingTree.getSrcNamespace().equals(target.getSrcNamespace())) {
                throw new IllegalStateException("Cannot apply mappings to differing namespaces. source: %s target: %s"
                        .formatted(mappingTree.getSrcNamespace(), target.getSrcNamespace()));
            }

            for (MappingTree.ClassMapping sourceClass : mappingTree.getClasses()) {
                final MappingTree.ClassMapping targetClass = target.getClass(sourceClass.getSrcName());

                if (targetClass == null) {
                    LOGGER.warn(
                            "Could not find provided javadoc target class {} from mod {}",
                            sourceClass.getSrcName(),
                            modId);
                    continue;
                }

                applyComment(sourceClass, targetClass);

                for (MappingTree.FieldMapping sourceField : sourceClass.getFields()) {
                    final MappingTree.FieldMapping targetField =
                            targetClass.getField(sourceField.getSrcName(), sourceField.getSrcDesc());

                    if (targetField == null) {
                        LOGGER.warn(
                                "Could not find provided javadoc target field {}{} from mod {}",
                                sourceField.getSrcName(),
                                sourceField.getSrcDesc(),
                                modId);
                        continue;
                    }

                    applyComment(sourceField, targetField);
                }

                for (MappingTree.MethodMapping sourceMethod : sourceClass.getMethods()) {
                    final MappingTree.MethodMapping targetMethod =
                            targetClass.getMethod(sourceMethod.getSrcName(), sourceMethod.getSrcDesc());

                    if (targetMethod == null) {
                        LOGGER.warn(
                                "Could not find provided javadoc target method {}{} from mod {}",
                                sourceMethod.getSrcName(),
                                sourceMethod.getSrcDesc(),
                                modId);
                        continue;
                    }

                    applyComment(sourceMethod, targetMethod);
                }
            }
        }

        private <T extends MappingTree.ElementMapping> void applyComment(T source, T target) {
            String sourceComment = source.getComment();

            if (sourceComment == null) {
                LOGGER.warn("Mod {} provided javadoc has mapping for {}, without comment", modId, source);
                return;
            }

            String targetComment = target.getComment();

            if (targetComment == null) {
                targetComment = "";
            } else {
                targetComment += "\n";
            }

            targetComment += sourceComment;
            target.setComment(targetComment);
        }

        // Must override as not to include MemoryMappingTree
        @Override
        public int hashCode() {
            return Objects.hash(modId, mappingsHash);
        }

        @Override
        public String toString() {
            return "ModJavadoc{modId='%s', mappingsHash='%s'}".formatted(modId, mappingsHash);
        }
    }
}

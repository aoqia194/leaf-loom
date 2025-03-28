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
package dev.aoqia.leaf.loom.configuration.providers.mappings;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import dev.aoqia.leaf.loom.api.mappings.layered.spec.FileMappingsSpecBuilder;
import dev.aoqia.leaf.loom.api.mappings.layered.spec.FileSpec;
import dev.aoqia.leaf.loom.api.mappings.layered.spec.LayeredMappingSpecBuilder;
import dev.aoqia.leaf.loom.api.mappings.layered.spec.MappingsSpec;
import dev.aoqia.leaf.loom.configuration.providers.mappings.extras.signatures.SignatureFixesSpec;
import dev.aoqia.leaf.loom.configuration.providers.mappings.file.FileMappingsSpecBuilderImpl;
import org.gradle.api.Action;

public class LayeredMappingSpecBuilderImpl implements LayeredMappingSpecBuilder {
    private final List<MappingsSpec<?>> layers = new LinkedList<>();

    public LayeredMappingSpec build() {
        List<MappingsSpec<?>> builtLayers = new LinkedList<>();
        // Official is always the base layer
        // builtLayers.add(new IntermediaryMappingsSpec());
        builtLayers.addAll(layers);

        return new LayeredMappingSpec(Collections.unmodifiableList(builtLayers));
    }

    @Override
    public LayeredMappingSpecBuilder addLayer(MappingsSpec<?> mappingSpec) {
        layers.add(mappingSpec);
        return this;
    }

    @Override
    public LayeredMappingSpecBuilder signatureFix(Object object) {
        return addLayer(new SignatureFixesSpec(FileSpec.create(object)));
    }

    @Override
    public LayeredMappingSpecBuilder mappings(Object file, Action<? super FileMappingsSpecBuilder> action) {
        FileMappingsSpecBuilderImpl builder = FileMappingsSpecBuilderImpl.builder(FileSpec.create(file));
        action.execute(builder);
        return addLayer(builder.build());
    }
}

/*
 * This file is part of leaf-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021 aoqia, FabricMC
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
package dev.aoqia.leaf.loom.test.unit.layeredmappings


import spock.lang.Specification

import spock.lang.Specification

import dev.aoqia.leaf.loom.configuration.providers.mappings.LayeredMappingSpec
import dev.aoqia.leaf.loom.configuration.providers.mappings.LayeredMappingSpecBuilderImpl
import dev.aoqia.leaf.loom.configuration.providers.mappings.file.FileMappingsSpec
import dev.aoqia.leaf.loom.configuration.providers.mappings.utils.MavenFileSpec
import dev.aoqia.leaf.loom.util.ClosureAction

class LayeredMappingSpecBuilderTest extends Specification {
	def "simple mojmap" () {
		when:
		def spec = layered {
			officialMojangMappings()
		}
		def layers = spec.layers()
		then:
		layers.size() == 2
		spec.version == "layered+hash.2198"
		layers[0].class == IntermediaryMappingsSpec
		layers[1].class == MojangMappingsSpec
	}

	def "yarn through file mappings"() {
		when:
		def spec = layered {
			mappings("dev.aoqia:yarn:41.78.16+build.1:v2")
		}
		def layers = spec.layers()
		then:
		spec.version == "layered+hash.1133958200"
		layers.size() == 2
		layers[0].class == IntermediaryMappingsSpec
		layers[1].class == FileMappingsSpec
		((layers[1] as FileMappingsSpec).fileSpec() as MavenFileSpec).dependencyNotation() == "dev.aoqia:yarn:41.78.16+build.1:v2"
	}

	LayeredMappingSpec layered(@DelegatesTo(LayeredMappingSpecBuilderImpl) Closure cl) {
		LayeredMappingSpecBuilderImpl builder = new LayeredMappingSpecBuilderImpl()
		new ClosureAction(cl).execute(builder)
		return builder.build()
	}
}

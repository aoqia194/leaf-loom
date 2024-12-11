/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021 FabricMC
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

package net.aoqia.loom.test.unit.layeredmappings

import java.nio.file.Path
import java.util.zip.ZipFile

import groovy.transform.EqualsAndHashCode
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.logging.Logger
import spock.lang.Specification

import net.fabricmc.mappingio.MappingReader
import net.fabricmc.mappingio.adapter.MappingDstNsReorder
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch
import net.fabricmc.mappingio.format.tiny.Tiny2FileWriter
import net.fabricmc.mappingio.tree.MemoryMappingTree

import groovy.transform.EqualsAndHashCode
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.logging.Logger
import spock.lang.Specification

import net.aoqia.loom.api.mappings.layered.MappingContext
import net.aoqia.loom.api.mappings.layered.MappingLayer
import net.aoqia.loom.api.mappings.layered.MappingsNamespace
import net.aoqia.loom.api.mappings.layered.spec.MappingsSpec
import net.aoqia.loom.configuration.providers.mappings.LayeredMappingSpec
import net.aoqia.loom.configuration.providers.mappings.LayeredMappingsProcessor
import net.aoqia.loom.configuration.providers.mappings.extras.unpick.UnpickLayer
import net.aoqia.loom.configuration.providers.mappings.utils.AddConstructorMappingVisitor
import net.aoqia.loom.configuration.providers.zomboid.ZomboidProvider
import net.aoqia.loom.test.LoomTestConstants
import net.aoqia.loom.util.copygamefile.CopyGameFile
import net.aoqia.loom.util.copygamefile.CopyGameFileBuilder
import net.aoqia.loom.util.download.Download
import net.aoqia.loom.util.download.DownloadBuilder

abstract class LayeredMappingsSpecification extends Specification implements LayeredMappingsTestConstants {
	Logger mockLogger = Mock(Logger)
	ZomboidProvider mockMinecraftProvider = Mock(ZomboidProvider)
	String intermediaryUrl
	MappingContext mappingContext = new TestMappingContext()

	File tempDir = new File(LoomTestConstants.TEST_DIR, "layered/${getClass().name}")

	Map<String, File> mavenFiles = [:]

	def withMavenFile(String mavenNotation, File file) {
		mavenFiles.put(mavenNotation, file)
	}

	File downloadFile(String url, String name) {
		File dst = new File(tempDir, name)
		if (!dst.exists()) {
			CopyGameFile.create(url)
					.defaultCache()
					.copyGameFileFromPath(dst.toPath())
		}
		return dst
	}

	File extractFileFromZip(File zipFile, String name) {
		File dst = new File(tempDir, name)
		dst.parentFile.mkdirs()

		new ZipFile(zipFile).withCloseable {
			dst << it.getInputStream(it.getEntry(name))
		}
		return dst
	}

	MemoryMappingTree getSingleMapping(MappingsSpec<? extends MappingLayer> spec) {
		MemoryMappingTree mappingTree = new MemoryMappingTree()
		spec.createLayer(mappingContext).visit(mappingTree)
		return mappingTree
	}

	MemoryMappingTree getLayeredMappings(MappingsSpec<? extends MappingLayer>... specs) {
		LayeredMappingsProcessor processor = createLayeredMappingsProcessor(specs)
		return processor.getMappings(processor.resolveLayers(mappingContext))
	}

	UnpickLayer.UnpickData getUnpickData(MappingsSpec<? extends MappingLayer>... specs) {
		LayeredMappingsProcessor processor = createLayeredMappingsProcessor(specs)
		return processor.getUnpickData(processor.resolveLayers(mappingContext))
	}

	private static LayeredMappingsProcessor createLayeredMappingsProcessor(MappingsSpec<? extends MappingLayer>... specs) {
		LayeredMappingSpec spec = new LayeredMappingSpec(specs.toList())
		return new LayeredMappingsProcessor(spec, false)
	}

	String getTiny(MemoryMappingTree mappingTree) {
		def sw = new StringWriter()
		mappingTree.accept(new Tiny2FileWriter(sw, false))
		return sw.toString()
	}

	MemoryMappingTree reorder(MemoryMappingTree mappingTree) {
		def reorderedMappings = new MemoryMappingTree()
		def nsReorder = new MappingDstNsReorder(reorderedMappings,
				Collections.singletonList(MappingsNamespace.NAMED.toString()))
		def nsSwitch = new MappingSourceNsSwitch(nsReorder, MappingsNamespace.INTERMEDIARY.toString(), true)
		def addConstructor = new AddConstructorMappingVisitor(nsSwitch)
		mappingTree.accept(addConstructor)
		return reorderedMappings
	}

	def setup() {
		mockMinecraftProvider.file(_) >> { args ->
			return new File(tempDir, args[0])
		}
	}

	class TestMappingContext implements MappingContext {
		@Override
		Path resolveDependency(Dependency dependency) {
			throw new UnsupportedOperationException("TODO")
		}

		@Override
		Path resolveDependency(MinimalExternalModuleDependency dependency) {
			throw new UnsupportedOperationException("TODO")
		}

		@Override
		Path resolveMavenDependency(String mavenNotation) {
			assert mavenFiles.containsKey(mavenNotation)
			return mavenFiles.get(mavenNotation).toPath()
		}

		@Override
		ZomboidProvider zomboidProvider() {
			return mockMinecraftProvider
		}

		@Override
		Path workingDirectory(String name) {
			return new File(tempDir, name).toPath()
		}

		@Override
		Logger getLogger() {
			return mockLogger
		}

		@Override
		DownloadBuilder download(String url) {
			return Download.create(url)
		}

		CopyGameFileBuilder copyGameFile(String path) {
			return CopyGameFile.create(path)
		}

		@Override
		boolean refreshDeps() {
			return false
		}
	}
}

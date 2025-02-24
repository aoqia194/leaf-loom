/*
 * This file is part of leaf-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2024 aoqia, FabricMC
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
package dev.aoqia.leaf.loom.test.unit.processor

import java.nio.file.Path
import java.util.function.Consumer

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import spock.lang.Specification
import spock.lang.TempDir

import net.fabricmc.mappingio.MappingReader
import net.fabricmc.mappingio.tree.MemoryMappingTree
import net.fabricmc.tinyremapper.TinyRemapper

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import spock.lang.Specification
import spock.lang.TempDir

import dev.aoqia.leaf.loom.api.mappings.layered.MappingsNamespace
import dev.aoqia.leaf.loom.api.processor.ProcessorContext
import dev.aoqia.leaf.loom.api.processor.SpecContext
import dev.aoqia.leaf.loom.configuration.ifaceinject.InterfaceInjectionProcessor
import dev.aoqia.leaf.loom.test.unit.processor.classes.AdvancedGenericInterface
import dev.aoqia.leaf.loom.test.unit.processor.classes.AdvancedGenericTargetClass
import dev.aoqia.leaf.loom.test.unit.processor.classes.DoubleGenericTargetClass
import dev.aoqia.leaf.loom.test.unit.processor.classes.DoublePassingGenericInterface
import dev.aoqia.leaf.loom.test.unit.processor.classes.DoublePassingGenericTargetClass
import dev.aoqia.leaf.loom.test.unit.processor.classes.FirstGenericInterface
import dev.aoqia.leaf.loom.test.unit.processor.classes.GenericInterface
import dev.aoqia.leaf.loom.test.unit.processor.classes.GenericTargetClass
import dev.aoqia.leaf.loom.test.unit.processor.classes.PassingGenericTargetClass
import dev.aoqia.leaf.loom.test.unit.processor.classes.SecondGenericInterface
import dev.aoqia.leaf.loom.test.unit.processor.classes.SelfGenericInterface
import dev.aoqia.leaf.loom.test.unit.processor.classes.SelfGenericTargetClass
import dev.aoqia.leaf.loom.test.unit.processor.classes.SimpleInterface
import dev.aoqia.leaf.loom.test.unit.processor.classes.SimpleTargetClass
import dev.aoqia.leaf.loom.util.Constants
import dev.aoqia.leaf.loom.util.LazyCloseable
import dev.aoqia.leaf.loom.util.Pair
import dev.aoqia.leaf.loom.util.TinyRemapperHelper
import dev.aoqia.leaf.loom.util.ZipUtils

class InterfaceInjectionProcessorTest extends Specification {
	@TempDir
	Path tempDir

	def "interface injection"() {
		given:
		def fmj = Mock(LeafModJson.Mockable)
		fmj.getId() >> "modid"
		fmj.getCustom(Constants.CustomModJsonKeys.INJECTED_INTERFACE) >> createCustomObject(key, value)

		def specContext = Mock(SpecContext)
		specContext.localMods() >> [fmj]
		specContext.modDependenciesCompileRuntime() >> []

		def processorContext = Mock(ProcessorContext)
		processorContext.getMappings() >> createMappings()

		def jar = tempDir.resolve("test.jar")
		packageJar(jar)

		processorContext.createRemapper(MappingsNamespace.OFFICIAL, MappingsNamespace.NAMED) >> createRemapper(jar, processorContext.getMappings())

		when:
		def processor = new TestInterfaceInjectionProcessor()
		def spec = processor.buildSpec(specContext)
		processor.processJar(jar, spec, processorContext)

		then:
		spec != null

		withTargetClass(jar, target, validator)

		where:
		key | value | target | validator
		// Simple class with a simple interface
		"class_1" | "dev/aoqia/loom/test/unit/processor/classes/SimpleInterface" | SimpleTargetClass.class | { Class<?> loadedClass ->
			loadedClass.interfaces.first().name == "dev/aoqia/loom/test/unit/processor/classes/SimpleInterface"
			loadedClass.constructors.first().newInstance().injectedMethod() == 123
		}

		// Inner class with a simple interface
		"class_1\$class_2" | "dev/aoqia/loom/test/unit/processor/classes/SimpleInterface" | SimpleTargetClass.Inner.class | { Class<?> loadedClass ->
			loadedClass.interfaces.first().name == "dev/aoqia/loom/test/unit/processor/classes/SimpleInterface"
			loadedClass.constructors.first().newInstance().injectedMethod() == 123
		}

		// Class using interface with generics
		"class_3" | "dev/aoqia/loom/test/unit/processor/classes/GenericInterface<Ljava/lang/String;>" | GenericTargetClass.class | { Class<?> loadedClass ->
			loadedClass.interfaces.first().name == "dev/aoqia/loom/test/unit/processor/classes/GenericInterface"
			loadedClass.constructors.first().newInstance().genericInjectedMethod() == null
		}

		// Class using generics and passing them to interface
		"class_4" | "dev/aoqia/loom/test/unit/processor/classes/GenericInterface<TT;>" | PassingGenericTargetClass.class | { Class<?> loadedClass ->
			loadedClass.interfaces.first().name == "dev/aoqia/loom/test/unit/processor/classes/GenericInterface"
			loadedClass.constructors.first().newInstance().genericInjectedMethod() == null
		}

		// Class having one injected interface with two generics, including one provided by the class
		"class_5" | "dev/aoqia/loom/test/unit/processor/classes/AdvancedGenericInterface<Ljava/util/function/Predicate<TT;>;Ljava/lang/Integer;>" | AdvancedGenericTargetClass.class | { Class<?> loadedClass ->
			loadedClass.interfaces.first().name == "dev/aoqia/loom/test/unit/processor/classes/AdvancedGenericInterface"
			loadedClass.constructors.first().newInstance().advancedGenericInjectedMethod().getClass() == AdvancedGenericTargetClass.Pair.class
		}

		// Class having two injected interfaces with one generic for each of them, including one provided by the class
		"class_7" | "dev/aoqia/loom/test/unit/processor/classes/FirstGenericInterface<Ljava/util/function/Predicate<TT;>;>" | DoubleGenericTargetClass.class | { Class<?> loadedClass ->
			loadedClass.interfaces.first().name == "dev/aoqia/loom/test/unit/processor/classes/FirstGenericInterface"
			loadedClass.constructors.first().newInstance().firstGenericInjectedMethod() == null
		}
		"class_7" | "dev/aoqia/loom/test/unit/processor/classes/SecondGenericInterface<Ljava/lang/Integer;>" | DoubleGenericTargetClass.class | { Class<?> loadedClass ->
			loadedClass.interfaces.last().name == "dev/aoqia/loom/test/unit/processor/classes/SecondGenericInterface"
			loadedClass.constructors.last().newInstance().secondGenericInjectedMethod() == null
		}

		// Self Generic Types + Signature Remapping Check
		"class_8" | "dev/aoqia/loom/test/unit/processor/classes/SelfGenericInterface<Lclass_7;>" | SelfGenericTargetClass.class | { Class<?> loadedClass ->
			loadedClass.interfaces.first().name == "dev/aoqia/loom/test/unit/proessor/classes/SelfGenericInterface"
			loadedClass.constructors.first().newInstance().selfGenericInjectedMethod() == null
		}

		// Class using double generics and passing them to the interface
		"class_9" | "dev/aoqia/loom/test/unit/processor/classes/DoublePassingGenericInterface<TF;TS;>" | DoublePassingGenericTargetClass.class | { Class<?> loadedClass ->
			loadedClass.interfaces.first().name == "dev/aoqia/loom/test/unit/processor/classes/DoublePassingGenericTargetClass"
			loadedClass.constructors.first().newInstance().doublePassingGenericInjectedMethod().getClass() == DoublePassingGenericTargetClass.Pair.class
		}
	}

	def "nothing to inject"() {
		given:
		def specContext = Mock(SpecContext)
		specContext.localMods() >> []
		specContext.modDependenciesCompileRuntime() >> []

		when:
		def processor = new TestInterfaceInjectionProcessor()
		def spec = processor.buildSpec(specContext)

		then:
		spec == null
	}

	// Create the custom FMJ entry for the injected interface
	static JsonObject createCustomObject(String key, String value) {
		def jsonObject = new JsonObject()
		def jsonArray = new JsonArray()
		jsonArray.add(value)
		jsonObject.add(key, jsonArray)
		return jsonObject
	}

	// Package the test classes into a jar file
	static void packageJar(Path path) {
		def entries = CLASSES_TO_PACKAGE.collect {
			def entryName = it.name.replace('.', '/') + ".class"
			new Pair(entryName, getClassBytes(it))
		}

		ZipUtils.add(path, entries)
	}

	static MemoryMappingTree createMappings() {
		def mappings = new MemoryMappingTree()
		new StringReader(MAPPINGS).withCloseable {
			MappingReader.read(it, mappings)
		}
		return mappings
	}

	static LazyCloseable<TinyRemapper> createRemapper(Path jar, MemoryMappingTree mappings) {
		return new LazyCloseable<>({
			TinyRemapper.Builder builder = TinyRemapper.newRemapper()
			builder.withMappings(TinyRemapperHelper.create(mappings, MappingsNamespace.OFFICIAL.toString(), MappingsNamespace.NAMED.toString(), false))
			TinyRemapper tinyRemapper = builder.build()
			tinyRemapper.readClassPath(jar)
			return tinyRemapper
		}, { tinyRemapper -> tinyRemapper.finish() })
	}

	// Load a class from a jar file and execute a closure with it
	static void withTargetClass(Path jar, Class<?> clazz, Consumer<Class<?>> closure) {
		// Groovy is needed as the test classes are compiled with it
		URL[] urls = [
			jar.toUri().toURL(),
			GroovyObject.class.protectionDomain.codeSource.location
		]

		new URLClassLoader("InterfaceInjectionTest", urls, null).withCloseable {
			def loadedClass = Class.forName(clazz.name, true, it)
			closure(loadedClass)
		}
	}

	static byte[] getClassBytes(Class<?> clazz) {
		return clazz.classLoader.getResourceAsStream(clazz.name.replace('.', '/') + ".class").withCloseable {
			it.bytes
		}
	}

	private class TestInterfaceInjectionProcessor extends InterfaceInjectionProcessor {
		TestInterfaceInjectionProcessor() {
			super("InterfaceInjection", true)
		}
	}

	private static final List<Class<?>> CLASSES_TO_PACKAGE = [
		SimpleTargetClass.class,
		SimpleTargetClass.Inner.class,
		SimpleInterface.class,
		GenericTargetClass.class,
		PassingGenericTargetClass.class,
		GenericInterface.class,
		AdvancedGenericTargetClass.class,
		AdvancedGenericTargetClass.Pair.class,
		AdvancedGenericInterface.class,
		DoubleGenericTargetClass.class,
		FirstGenericInterface.class,
		SecondGenericInterface.class,
		SelfGenericTargetClass.class,
		SelfGenericInterface.class,
		DoublePassingGenericTargetClass.class,
		DoublePassingGenericTargetClass.Pair.class,
		DoublePassingGenericInterface.class
	]

	private static final String MAPPINGS = """
tiny\t2\t0\tofficial\tnamed
c\tclass_1\tdev/aoqia/loom/test/unit/processor/classes/SimpleTargetClass
c\tclass_1\$class_2\tdev/aoqia/loom/test/unit/processor/classes/SimpleTargetClass\$Inner
c\tclass_3\tdev/aoqia/loom/test/unit/processor/classes/GenericTargetClass
c\tclass_4\tdev/aoqia/loom/test/unit/processor/classes/PassingGenericTargetClass
c\tclass_5\tdev/aoqia/loom/test/unit/processor/classes/AdvancedGenericTargetClass
c\tclass_5\$class_6\tdev/aoqia/loom/test/unit/processor/classes/AdvancedGenericTargetClass\$Pair
c\tclass_7\tdev/aoqia/loom/test/unit/processor/classes/DoubleGenericTargetClass
c\tclass_8\tdev/aoqia/loom/test/unit/processor/classes/SelfGenericTargetClass
c\tclass_9\tdev/aoqia/loom/test/unit/processor/classes/DoublePassingGenericTargetClass
c\tclass_9\$class_10\tdev/aoqia/loom/test/unit/processor/classes/DoublePassingGenericTargetClass\$Pair
""".trim()
}

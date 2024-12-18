/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2023 FabricMC
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

package net.aoqia.loom.test.unit.library.processors

import org.gradle.api.JavaVersion
import spock.lang.Specification

import org.gradle.api.JavaVersion
import spock.lang.Specification

import net.aoqia.loom.configuration.providers.zomboid.library.Library
import net.aoqia.loom.configuration.providers.zomboid.library.LibraryContext
import net.aoqia.loom.configuration.providers.zomboid.library.LibraryProcessorManager
import net.aoqia.loom.configuration.providers.zomboid.library.ZomboidLibraryHelper
import net.aoqia.loom.test.util.GradleTestUtil
import net.aoqia.loom.test.util.ZomboidTestUtils
import net.aoqia.loom.util.Platform

abstract class LibraryProcessorTest extends Specification {
	def getLibs(String id, Platform platform, JavaVersion javaVersion = JavaVersion.VERSION_17) {
		def meta = ZomboidTestUtils.getVersionMeta(id)
		def libraries = ZomboidLibraryHelper.getLibrariesForPlatform(meta, platform)
		def context = new LibraryContext(meta, javaVersion)
		return [libraries, context]
	}

	boolean hasLibrary(String name, List<Library> libraries) {
		libraries.any { it.is(name) }
	}

	Library findLibrary(String name, List<Library> libraries) {
		libraries.find { it.is(name) }
	}

	LibraryProcessorManager mockLibraryProcessorManager() {
		def libraryProcessorManager = new LibraryProcessorManager(null, GradleTestUtil.mockRepositoryHandler())
		return libraryProcessorManager
	}
}

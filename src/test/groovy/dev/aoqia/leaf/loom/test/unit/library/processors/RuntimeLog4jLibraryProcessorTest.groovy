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
package dev.aoqia.leaf.loom.test.unit.library.processors

import dev.aoqia.loom.test.util.PlatformTestUtils

import dev.aoqia.loom.test.util.PlatformTestUtils

import dev.aoqia.leaf.loom.configuration.providers.zomboid.library.Library
import dev.aoqia.leaf.loom.configuration.providers.zomboid.library.LibraryProcessor
import dev.aoqia.leaf.loom.configuration.providers.zomboid.library.processors.RuntimeLog4jLibraryProcessor

import dev.aoqia.loom.test.util.PlatformTestUtils

class RuntimeLog4jLibraryProcessorTest extends LibraryProcessorTest {
	def "Make log4j runtime"() {
		when:
		def (original, context) = getLibs("41.78.16", PlatformTestUtils.MAC_OS_X64)
		def processor = new RuntimeLog4jLibraryProcessor(PlatformTestUtils.MAC_OS_X64, context)
		def processed = mockLibraryProcessorManager().processLibraries([processor], original)

		then:
		processor.applicationResult == LibraryProcessor.ApplicationResult.CAN_APPLY

		findLibrary("org.apache.logging.log4j", original).target() == Library.Target.COMPILE
		findLibrary("org.apache.logging.log4j", processed).target() == Library.Target.RUNTIME
	}
}

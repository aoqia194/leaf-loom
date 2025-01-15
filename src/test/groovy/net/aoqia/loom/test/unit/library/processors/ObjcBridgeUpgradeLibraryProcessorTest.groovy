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
package net.aoqia.loom.test.unit.library.processors

import net.aoqia.loom.configuration.providers.zomboid.library.Library
import net.aoqia.loom.configuration.providers.zomboid.library.LibraryProcessor
import net.aoqia.loom.configuration.providers.zomboid.library.processors.ObjcBridgeUpgradeLibraryProcessor
import net.aoqia.loom.test.util.PlatformTestUtils

class ObjcBridgeUpgradeLibraryProcessorTest extends LibraryProcessorTest {
	def "Only apply to arm64 macOS"() {
		when:
		def (_, context) = getLibs("41.78.16", platform)
		def processor = new ObjcBridgeUpgradeLibraryProcessor(platform, context)
		then:
		processor.applicationResult == result

		where:
		platform                        || result
		PlatformTestUtils.MAC_OS_ARM64  || LibraryProcessor.ApplicationResult.MUST_APPLY
		PlatformTestUtils.MAC_OS_X64    || LibraryProcessor.ApplicationResult.DONT_APPLY
		PlatformTestUtils.LINUX_ARM64   || LibraryProcessor.ApplicationResult.DONT_APPLY
		PlatformTestUtils.LINUX_X64     || LibraryProcessor.ApplicationResult.DONT_APPLY
		PlatformTestUtils.WINDOWS_ARM64 || LibraryProcessor.ApplicationResult.DONT_APPLY
		PlatformTestUtils.WINDOWS_X64   || LibraryProcessor.ApplicationResult.DONT_APPLY
	}

	def "only apply to unsupported versions"() {
		when:
		def (_, context) = getLibs(id, PlatformTestUtils.MAC_OS_ARM64)
		def processor = new ObjcBridgeUpgradeLibraryProcessor(PlatformTestUtils.MAC_OS_ARM64, context)
		then:
		processor.applicationResult == result

		where:
		id       || result
		"41.78.16" || LibraryProcessor.ApplicationResult.DONT_APPLY
		"41.78.15" || LibraryProcessor.ApplicationResult.MUST_APPLY
		"41.78.13" || LibraryProcessor.ApplicationResult.MUST_APPLY
		"41.78.0" || LibraryProcessor.ApplicationResult.MUST_APPLY
		"41.77.9" || LibraryProcessor.ApplicationResult.DONT_APPLY
		"41.77.0" || LibraryProcessor.ApplicationResult.DONT_APPLY
	}

	def "Upgrade objc bridge"() {
		when:
		def (original, context) = getLibs(id, PlatformTestUtils.MAC_OS_ARM64)
		def processor = new ObjcBridgeUpgradeLibraryProcessor(PlatformTestUtils.MAC_OS_ARM64, context)
		def processed = mockLibraryProcessorManager().processLibraries([processor], original)

		then:
		// Test that we always compile against the original version
		original.find { it.is("ca.weblite:java-objc-bridge") && it.target() == Library.Target.COMPILE }.version() == "1.0.0"
		processed.find { it.is("ca.weblite:java-objc-bridge") && it.target() == Library.Target.COMPILE }.version() == "1.0.0"

		// Test that we use 1.1 at runtime
		processed.find { it.is("ca.weblite:java-objc-bridge") && it.target() == Library.Target.RUNTIME }.version() == "1.1"

		// Test that we removed the natives, as they are included in the jar on the runtime classpath
		original.find { it.is("ca.weblite:java-objc-bridge") && it.target() == Library.Target.NATIVES } != null
		processed.find { it.is("ca.weblite:java-objc-bridge") && it.target() == Library.Target.NATIVES } == null

		where:
		id       | _
		"41.78.15" | _
		"41.78.13" | _
	}
}

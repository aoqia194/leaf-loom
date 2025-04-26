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
package dev.aoqia.leaf.loom.test.unit.library.processors

import dev.aoqia.loom.test.util.PlatformTestUtils

import dev.aoqia.loom.test.util.PlatformTestUtils

import dev.aoqia.leaf.loom.configuration.providers.zomboid.library.Library
import dev.aoqia.leaf.loom.configuration.providers.zomboid.library.LibraryProcessor
import dev.aoqia.leaf.loom.configuration.providers.zomboid.library.processors.RiscVNativesLibraryProcessor

import dev.aoqia.loom.test.util.PlatformTestUtils

class RiscVNativesLibraryProcessorTest extends LibraryProcessorTest {
	def "Apply when adding linux riscv support"() {
		when:
		def (_, context) = getLibs(id, PlatformTestUtils.LINUX_RISCV)
		def processor = new RiscVNativesLibraryProcessor(PlatformTestUtils.LINUX_RISCV, context)
		then:
		processor.applicationResult == result

		where:
		// Don't apply RISCV when we dont use classpath natives or when the game using LWJGL version <3
		id       || result
		"41.78.16" || LibraryProcessor.ApplicationResult.MUST_APPLY
		"41.78.15" || LibraryProcessor.ApplicationResult.MUST_APPLY
		"41.78.13" || LibraryProcessor.ApplicationResult.DONT_APPLY // In reality we dont apply here, just test.
	}

	def "Never apply on none riscv platforms"() {
		when:
		def (_, context) = getLibs(id, platform)
		def processor = new RiscVNativesLibraryProcessor(platform, context)
		then:
		processor.applicationResult == LibraryProcessor.ApplicationResult.DONT_APPLY

		where:
		id       | platform
		"41.78.16" | PlatformTestUtils.LINUX_ARM64
		"41.78.16" | PlatformTestUtils.LINUX_X64
		"41.78.15" | PlatformTestUtils.MAC_OS_X64
		"41.78.13" | PlatformTestUtils.WINDOWS_X64
		"41.78.0" | PlatformTestUtils.MAC_OS_X64
		"41.77.9" | PlatformTestUtils.MAC_OS_X64
		"41.77.8" | PlatformTestUtils.LINUX_X64
		"41.77.0" | PlatformTestUtils.MAC_OS_X64
		"41.65.0" | PlatformTestUtils.WINDOWS_X64
	}

	def "Add linux riscv natives"() {
		when:
		def (original, context) = getLibs("41.78.16", PlatformTestUtils.LINUX_RISCV)
		def processor = new RiscVNativesLibraryProcessor(PlatformTestUtils.LINUX_RISCV, context)
		def processed = mockLibraryProcessorManager().processLibraries([processor], original)

		then:
		// Test that the riscv64 natives are added alongside the existing ones
		def originalNatives = original.findAll { it.is("org.lwjgl") && it.target() == Library.Target.NATIVES }
		originalNatives.count { it.classifier() == "natives-linux-riscv64" } == 0
		originalNatives.count { it.classifier() == "natives-linux" } > 0

		def processedNatives = processed.findAll { it.is("org.lwjgl") && it.target() == Library.Target.NATIVES }
		processedNatives.count { it.classifier() == "natives-linux-riscv64" } > 0
		processedNatives.count { it.classifier() == "natives-linux" } > 0
	}
}

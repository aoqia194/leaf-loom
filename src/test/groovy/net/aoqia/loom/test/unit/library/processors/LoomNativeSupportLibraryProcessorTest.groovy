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

import org.gradle.api.JavaVersion

import org.gradle.api.JavaVersion

import net.aoqia.loom.configuration.providers.zomboid.library.Library
import net.aoqia.loom.configuration.providers.zomboid.library.LibraryProcessor
import net.aoqia.loom.configuration.providers.zomboid.library.processors.LoomNativeSupportLibraryProcessor
import net.aoqia.loom.test.util.PlatformTestUtils

class LoomNativeSupportLibraryProcessorTest extends LibraryProcessorTest {
	def "Apply when adding macOS ARM64 support"() {
		when:
		def (_, context) = getLibs(id, PlatformTestUtils.MAC_OS_ARM64)
		def processor = new LoomNativeSupportLibraryProcessor(PlatformTestUtils.MAC_OS_ARM64, context)
		then:
		processor.applicationResult == result

		where:
		id       || result
		"41.78.16" || LibraryProcessor.ApplicationResult.CAN_APPLY
		"41.78.15" || LibraryProcessor.ApplicationResult.MUST_APPLY
		"41.78.13" || LibraryProcessor.ApplicationResult.MUST_APPLY
		"40.43.0" || LibraryProcessor.ApplicationResult.DONT_APPLY // No LWJGL 3
	}

	def "Apply when using Java 19 or later on macOS"() {
		when:
		def (_, context) = getLibs("41.78.16", PlatformTestUtils.MAC_OS_X64, version)
		def processor = new LoomNativeSupportLibraryProcessor(PlatformTestUtils.MAC_OS_X64, context)
		then:
		processor.applicationResult == result

		where:
		version                 || result
		JavaVersion.VERSION_20  || LibraryProcessor.ApplicationResult.CAN_APPLY
		JavaVersion.VERSION_19  || LibraryProcessor.ApplicationResult.CAN_APPLY
		JavaVersion.VERSION_17  || LibraryProcessor.ApplicationResult.CAN_APPLY
		JavaVersion.VERSION_1_8 || LibraryProcessor.ApplicationResult.CAN_APPLY
	}

	def "Dont apply when using Java 19 or later on macOS with supported version"() {
		when:
		def (_, context) = getLibs("41.78.16", PlatformTestUtils.MAC_OS_X64, version)
		def processor = new LoomNativeSupportLibraryProcessor(PlatformTestUtils.MAC_OS_X64, context)
		then:
		processor.applicationResult == result

		where:
		version                 || result
		JavaVersion.VERSION_20 || LibraryProcessor.ApplicationResult.CAN_APPLY
		JavaVersion.VERSION_19  || LibraryProcessor.ApplicationResult.CAN_APPLY
		JavaVersion.VERSION_17  || LibraryProcessor.ApplicationResult.CAN_APPLY
		JavaVersion.VERSION_1_8 || LibraryProcessor.ApplicationResult.CAN_APPLY
	}

	def "Can apply when using Java 19 or later on other platforms"() {
		when:
		def (_, context) = getLibs("41.78.16", PlatformTestUtils.WINDOWS_ARM64, version)
		def processor = new LoomNativeSupportLibraryProcessor(PlatformTestUtils.WINDOWS_ARM64, context)
		then:
		processor.applicationResult == result

		where:
		version                 || result
		JavaVersion.VERSION_20  || LibraryProcessor.ApplicationResult.CAN_APPLY
		JavaVersion.VERSION_19  || LibraryProcessor.ApplicationResult.CAN_APPLY
		JavaVersion.VERSION_17  || LibraryProcessor.ApplicationResult.CAN_APPLY
		JavaVersion.VERSION_1_8 || LibraryProcessor.ApplicationResult.CAN_APPLY
	}

	def "Can apply on other platforms"() {
		when:
		def (_, context) = getLibs(id, platform)
		def processor = new LoomNativeSupportLibraryProcessor(platform, context)
		then:
		processor.applicationResult == LibraryProcessor.ApplicationResult.CAN_APPLY

		where:
		id       | platform
		"41.78.16" | PlatformTestUtils.MAC_OS_ARM64
		"41.78.15" | PlatformTestUtils.WINDOWS_X64
		"41.78.16" | PlatformTestUtils.MAC_OS_X64
		"41.78.13" | PlatformTestUtils.WINDOWS_ARM64
		"41.78.15" | PlatformTestUtils.LINUX_X64
		"41.77.9" | PlatformTestUtils.MAC_OS_X64
		"41.78.0" | PlatformTestUtils.WINDOWS_X64
	}

	def "Add native support mod"() {
		when:
		def (original, context) = getLibs("41.78.16", PlatformTestUtils.MAC_OS_X64)
		def processor = new LoomNativeSupportLibraryProcessor(PlatformTestUtils.MAC_OS_X64, context)
		def processed = mockLibraryProcessorManager().processLibraries([processor], original)

		then:
		// Test to ensure that we added the mod
		original.find { it.is("net.fabricmc:fabric-loom-native-support") && it.target() == Library.Target.LOCAL_MOD } == null
		processed.find { it.is("net.fabricmc:fabric-loom-native-support") && it.target() == Library.Target.LOCAL_MOD } != null
	}
}

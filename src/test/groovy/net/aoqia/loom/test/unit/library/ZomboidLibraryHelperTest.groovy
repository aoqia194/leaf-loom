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
package net.aoqia.loom.test.unit.library

import spock.lang.Specification

import spock.lang.Specification

import net.aoqia.loom.configuration.providers.zomboid.library.Library
import net.aoqia.loom.configuration.providers.zomboid.library.ZomboidLibraryHelper
import net.aoqia.loom.test.util.PlatformTestUtils
import net.aoqia.loom.test.util.ZomboidTestUtils

class ZomboidLibraryHelperTest extends Specification {
	static List<String> VERSIONS = [
		"41.78.16",
		"41.78.15",
		"41.78.13",
	]

	def "get libraries"() {
		// A quick test to make sure that nothing fails when getting the libraries for all platforms on a range of
		// versions.
		when:
		def meta = ZomboidTestUtils.getVersionMeta(id)
		def libraries = ZomboidLibraryHelper.getLibrariesForPlatform(meta, platform)

		then:
		libraries.size() > 0

		where:
		platform << PlatformTestUtils.ALL * VERSIONS.size()
		id << VERSIONS * PlatformTestUtils.ALL.size()
	}

	def "find macos natives"() {
		when:
		def meta = ZomboidTestUtils.getVersionMeta("41.78.16")
		def libraries = ZomboidLibraryHelper.getLibrariesForPlatform(meta, PlatformTestUtils.MAC_OS_X64)

		then:
		libraries.find {
			it.is("ca.weblite:java-objc-bridge") && it.target() == Library.Target.NATIVES
		} != null
		libraries.find {
			it.is("org.lwjgl:lwjgl-glfw") && it.target() == Library.Target.NATIVES
		}.classifier() == "natives-macos"
	}

	def "dont find macos natives"() {
		when:
		def meta = ZomboidTestUtils.getVersionMeta("41.78.16")
		def libraries = ZomboidLibraryHelper.getLibrariesForPlatform(meta, PlatformTestUtils.WINDOWS_X64)

		then:
		libraries.find {
			it.is("ca.weblite:java-objc-bridge") && it.target() == Library.Target.NATIVES
		} == null
	}
}

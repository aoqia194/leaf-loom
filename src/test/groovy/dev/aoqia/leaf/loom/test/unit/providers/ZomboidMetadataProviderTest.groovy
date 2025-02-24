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
package dev.aoqia.leaf.loom.test.unit.providers

import java.nio.file.Files
import java.nio.file.Path

import org.intellij.lang.annotations.Language

import org.intellij.lang.annotations.Language

import dev.aoqia.leaf.loom.configuration.providers.zomboid.ManifestLocations
import dev.aoqia.leaf.loom.configuration.providers.zomboid.ZomboidMetadataProvider
import dev.aoqia.leaf.loom.util.copygamefile.CopyGameFile
import dev.aoqia.loom.test.LoomTestConstants
import dev.aoqia.loom.test.unit.download.DownloadTest

class ZomboidMetadataProviderTest extends DownloadTest {
	Path testDir

	def setup() {
		testDir = LoomTestConstants.TEST_DIR.toPath().resolve("ZomboidMetadataProviderTest")

		testDir.toFile().deleteDir()
		Files.createDirectories(testDir)
	}

	def "Cached result"() {
		setup:
		int calls = 0
		server.get("/versionManifest") {
			it.result(VERSION_MANIFEST_1)
			calls++
		}

		when:
		// Test the builtin caching of the metadata provider
		def metaProvider = provider("41.78.16")
		metaProvider.getVersionMeta()
		def meta = metaProvider.getVersionMeta()

		// Create a new provider to test download caching
		def meta2 = provider("41.78.16").getVersionMeta()

		then:
		meta.id() == "41.78.16"
		meta2.id() == "41.78.16"
		calls == 1
	}

	// Tests a fix to https://github.com/FabricMC/fabric-loom/issues/886
	def "Force download with new version"() {
		setup:
		int calls = 0
		server.get("/versionManifest") {
			it.result(calls == 0 ? VERSION_MANIFEST_1 : VERSION_MANIFEST_2)
			calls++
		}
		server.get("/experimentalVersionManifest") {
			it.result(EXP_VERSION_MANIFEST)
			calls++
		}

		when:
		def meta = provider("41.78.16").getVersionMeta()
		def meta2 = provider("41.78.16").getVersionMeta()

		then:
		meta.id() == "41.78.16"
		meta2.id() == "41.78.16"
		calls == 3
	}

	def "Experimental"() {
		setup:
		int calls = 0
		server.get("/versionManifest") {
			it.result(VERSION_MANIFEST_1)
			calls++
		}
		server.get("/experimentalVersionManifest") {
			it.result(EXP_VERSION_MANIFEST)
			calls++
		}

		when:
		def meta = provider("41.78.16").getVersionMeta()

		then:
		meta.id() == "41.78.16"
		calls == 2
	}

	def "Custom manifest"() {
		setup:
		server.get("/customManifest") {
			it.result('{"id": "2.0.0"}')
		}

		when:
		def meta = provider("2.0.0", "$DownloadTest.PATH/customManifest").getVersionMeta()

		then:
		meta.id() == "2.0.0"
	}

	def "Get unknown"() {
		setup:
		int calls = 0
		server.get("/versionManifest") {
			it.result(VERSION_MANIFEST_1)
			calls++
		}
		server.get("/experimentalVersionManifest") {
			it.result(EXP_VERSION_MANIFEST)
			calls++
		}

		when:
		provider("2.0.0").getVersionMeta()

		then:
		def e = thrown(RuntimeException)
		e.message == "Failed to find Zomboid version: 2.0.0"
		calls == 4
	}

	private ZomboidMetadataProvider provider(String version, String customUrl = null) {
		return new ZomboidMetadataProvider(
				options(version, customUrl),
				CopyGameFile.&create
				)
	}

	private ZomboidMetadataProvider.Options options(String version, String customUrl) {
		ManifestLocations manifests = new ManifestLocations()
		manifests.add("test", "$DownloadTest.PATH/versionManifest", 0)
		manifests.add("test_experimental", "$DownloadTest.PATH/experimentalVersionManifest", 1)

		return new ZomboidMetadataProvider.Options(
				version,
				manifests,
				customUrl,
				testDir,
				testDir
				)
	}

	@Language("json")
	private static final String VERSION_MANIFEST_1 = """
{
  "latest": {
    "release": "41.78.16",
    "snapshot": "41.78.16"
  },
  "versions": [
    {
      "id": "41.78.16",
      "url": "https://raw.githubusercontent.com/aoqia194/leaf/refs/heads/main/manifests/client/win/41.78.16.json",
      "sha1": "0b32879ed74dde574891dd60b597dca2bef6d814"
    }
  ]
}
"""

	@Language("json")
	private static final String VERSION_MANIFEST_2 = """
{
  "latest": {
    "release": "41.78.16",
    "snapshot": ""
  },
  "versions": [
    {
      "id": "41.78.16",
      "url": "https://raw.githubusercontent.com/aoqia194/leaf/refs/heads/main/manifests/client/win/41.78.16.json",
      "sha1": "0b32879ed74dde574891dd60b597dca2bef6d814"
    }
  ]
}
"""
}

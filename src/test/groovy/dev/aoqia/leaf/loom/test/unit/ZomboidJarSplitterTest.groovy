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
package dev.aoqia.leaf.loom.test.unit


import dev.aoqia.loom.test.LoomTestConstants
import dev.aoqia.loom.test.util.GradleTestUtil
import spock.lang.Specification

import dev.aoqia.loom.test.LoomTestConstants
import dev.aoqia.loom.test.util.GradleTestUtil
import spock.lang.Specification

import dev.aoqia.leaf.loom.configuration.providers.zomboid.ZomboidJarSplitter
import dev.aoqia.leaf.loom.util.copygamefile.CopyGameFile

import dev.aoqia.loom.test.LoomTestConstants
import dev.aoqia.loom.test.util.GradleTestUtil
import spock.lang.Specification

class ZomboidJarSplitterTest extends Specification {
	public static final String CLIENT_JAR_URL = "https://launcher.mojang.com/v1/objects/7e46fb47609401970e2818989fa584fd467cd036/client.jar"
	public static final String SERVER_BUNDLE_JAR_URL = "https://launcher.mojang.com/v1/objects/125e5adf40c659fd3bce3e66e67a16bb49ecc1b9/server.jar"

	public static final File mcJarDir = new File(LoomTestConstants.TEST_DIR, "jar-splitter")

	def "split jars"() {
		// TODO: Temporarily Disable.
		return

		given:
		def clientJar = downloadJarIfNotExists(CLIENT_JAR_URL, "client.jar")
		def serverJar = downloadJarIfNotExists(SERVER_BUNDLE_JAR_URL, "server_bundle.jar")

		def clientOnlyJar = new File(mcJarDir, "client_only.jar")
		def commonJar = new File(mcJarDir, "common.jar")
		when:
		def serverBundleMetadata = BundleMetadata.fromJar(serverBundleJar.toPath())
		serverBundleMetadata.versions().find().unpackEntry(serverBundleJar.toPath(), serverJar.toPath(), GradleTestUtil.mockProject())

		clientOnlyJar.delete()
		commonJar.delete()

		new ZomboidJarSplitter(clientJar.toPath(), serverJar.toPath()).withCloseable {
			it.split(clientOnlyJar.toPath(), commonJar.toPath())
		}
		then:
		serverBundleMetadata.versions().size() == 1
	}

	File downloadJarIfNotExists(String url, String name) {
		File dst = new File(mcJarDir, name)

		if (!dst.exists()) {
			dst.parentFile.mkdirs()
			CopyGameFile.create(url)
					.defaultCache()
					.copyGameFileFromPath(dst.toPath())
		}

		return dst
	}
}

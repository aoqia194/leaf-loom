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
package dev.aoqia.leaf.loom.test.util

import java.time.Duration

import com.google.gson.Gson
import com.google.gson.GsonBuilder

import dev.aoqia.leaf.loom.configuration.providers.zomboid.VersionsManifest
import dev.aoqia.leaf.loom.configuration.providers.zomboid.ZomboidVersionManifest
import dev.aoqia.loom.test.LoomTestConstants
import dev.aoqia.leaf.loom.util.MirrorUtil
import dev.aoqia.leaf.loom.util.copygamefile.CopyGameFile

class ZomboidTestUtils {
	private static final File TEST_DIR = new File(LoomTestConstants.TEST_DIR, "zomboid")
	public static final Gson GSON = new GsonBuilder().create()

	static ZomboidVersionManifest getVersionMeta(String id) {
		def versionManifest = download(MirrorUtil.getClientVersionManifests(null), "version_manifest.json")
		def manifest = GSON.fromJson(versionManifest, VersionsManifest.class)
		def version = manifest.versions().find {
			it.id == id
		}

		def metaJson = download(version.url, "${id}.json")
		GSON.fromJson(metaJson, ZomboidVersionManifest.class)
	}

	static String download(String url, String name) {
		CopyGameFile.create(url)
				.maxAge(Duration.ofDays(31))
				.downloadString(new File(TEST_DIR, name).toPath())
	}
}

/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2025 FabricMC
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

package dev.aoqia.leaf.loom.configuration.providers.zomboid.verify;

import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;

import javax.inject.Inject;

import org.gradle.api.Project;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.aoqia.leaf.loom.util.Checksum;

public abstract class ZomboidJarVerification {
	private static final Logger LOGGER = LoggerFactory.getLogger(ZomboidJarVerification.class);

	private final String gameVersion;

	@Inject
	protected abstract Project getProject();

	@Inject
	public ZomboidJarVerification(String gameVersion) {
		this.gameVersion = gameVersion;
	}

	private boolean isValidKnownVersion(Path path, String version, KnownJarType type) throws SignatureVerificationFailure {
		Map<String, String> knownVersions = type.getKnownVersions();
		String expectedHash = knownVersions.get(version);

		if (expectedHash == null) {
			return false;
		}

		LOGGER.info("Found executed hash ({}) for known version: {}", expectedHash, version);
		Checksum.Result hash = Checksum.of(path).sha256();

		if (hash.matchesStr(expectedHash)) {
			LOGGER.info("Game {} hash matches known version", path.getFileName());
			return true;
		}

		throw new SignatureVerificationFailure("Hash mismatch for known game version " + version + ": expected " + expectedHash + ", got " + hash);
	}

	private enum KnownJarType {
		CLIENT(KnownVersions::client),
		SERVER(KnownVersions::server),;

		private final Function<KnownVersions, Map<String, String>> knownVersions;

		KnownJarType(Function<KnownVersions, Map<String, String>> knownVersions) {
			this.knownVersions = knownVersions;
		}

		private Map<String, String> getKnownVersions() {
			return knownVersions.apply(KnownVersions.INSTANCE.get());
		}
	}
}

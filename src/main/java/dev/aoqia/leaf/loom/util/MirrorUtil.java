/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016-2021 FabricMC
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

package dev.aoqia.leaf.loom.util;

import org.gradle.api.plugins.ExtensionAware;

import java.nio.file.Path;

public class MirrorUtil {
    public static Path getGamePath(ExtensionAware aware) {
        final var ext = aware.getExtensions().getExtraProperties();
        if (ext.has("loom_game_path")) {
            return Path.of(String.valueOf(ext.get("loom_game_path")));
        }

        final var envVar = System.getenv("LEAF_GAME_PATH");
        if (envVar != null) {
            return Path.of(envVar);
        }

        return Constants.getDefaultGamePath();
    }

	public static String getClientVersionManifests(ExtensionAware aware) {
        final var ext = aware.getExtensions().getExtraProperties();
		if (ext.has("loom_client_version_manifests")) {
			return String.valueOf(ext.get("loom_version_manifests"));
		}

		return String.format("%s/index.json", Constants.VERSION_MANIFESTS);
	}

    public static String getServerVersionManifests(ExtensionAware aware) {
        final var ext = aware.getExtensions().getExtraProperties();
		if (ext.has("loom_server_version_manifests")) {
			return String.valueOf(ext.get("loom_version_manifests"));
		}

		return String.format("%s/server/%s/index.json", Constants.VERSION_MANIFESTS, getOsStringForUrl());
	}

    public static String getOsStringForUrl() throws RuntimeException {
        final Platform.OperatingSystem os = Platform.CURRENT.getOperatingSystem();
        if (os.isWindows()) {
            return "win";
        } else if (os.isMacOS()) {
            return "mac";
        } else if (os.isLinux()) {
            return "linux";
        }

        throw new RuntimeException("Unsupported operating system: " + os);
    }

	public static String getFabricRepository(ExtensionAware aware) {
        final var ext = aware.getExtensions().getExtraProperties();
		if (ext.has("loom_fabric_repository")) {
			return String.valueOf(ext.get("loom_fabric_repository"));
		}

		return Constants.FABRIC_REPOSITORY;
	}
}

/*
 * This file is part of leaf-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016-2021 aoqia, FabricMC
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


import java.nio.file.Path;

import org.gradle.api.plugins.ExtensionAware;

public class MirrorUtil {
    public static Path getClientGamePath(ExtensionAware aware) {
        final var ext = aware.getExtensions().getExtraProperties();
        if (ext.has("clientGamePath")) {
            // noinspection DataFlowIssue
            return Path.of(ext.get("clientGamePath").toString());
        }

        final var envVar = System.getenv("LEAF_CLIENT_GAME_PATH");
        if (envVar != null) {
            return Path.of(envVar);
        }

        return Constants.getDefaultClientGamePath();
    }

    public static Path getServerGamePath(ExtensionAware aware) {
        final var ext = aware.getExtensions().getExtraProperties();
        if (ext.has("serverGamePath")) {
            // noinspection DataFlowIssue
            return Path.of(ext.get("serverGamePath").toString());
        }

        final var envVar = System.getenv("LEAF_SERVER_GAME_PATH");
        if (envVar != null) {
            return Path.of(envVar);
        }

        return Constants.getDefaultServerGamePath();
    }

    public static String getClientVersionManifestUrl(ExtensionAware aware) {
        final var ext = aware.getExtensions().getExtraProperties();
        if (ext.has("clientVersionManifestUrl")) {
            // noinspection DataFlowIssue
            return ext.get("clientVersionManifestUrl").toString();
        }

        return String.format("%s/client/%s/version_manifest.json", Constants.VERSION_MANIFESTS,
            getOsStringForUrl());
    }

    public static String getServerVersionManifestUrl(ExtensionAware aware) {
        final var ext = aware.getExtensions().getExtraProperties();
        if (ext.has("serverVersionManifestUrl")) {
            // noinspection DataFlowIssue
            return ext.get("serverVersionManifestUrl").toString();
        }

        return String.format("%s/server/%s/version_manifest.json", Constants.VERSION_MANIFESTS,
            getOsStringForUrl());
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
        if (ext.has("fabricRepositoryUrl")) {
            // noinspection DataFlowIssue
            return ext.get("fabricRepositoryUrl").toString();
        }

        return Constants.FABRIC_REPOSITORY;
    }
}

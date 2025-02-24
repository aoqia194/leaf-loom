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


import org.gradle.api.plugins.ExtensionAware;
import org.jetbrains.annotations.Nullable;

public class MirrorUtil {
    public static String getGameInstallPath(ExtensionAware aware) {
        if (aware.getExtensions().getExtraProperties().has("loom_game_install_path")) {
            return String.valueOf(aware.getExtensions().getExtraProperties().get("loom_game_install_path"));
        }

        return Constants.GAME_INSTALL_PATH;
    }

    public static String getServerInstallPath(@Nullable ExtensionAware aware) {
        if (aware != null && aware.getExtensions().getExtraProperties().has("loom_server_install_path")) {
            return String.valueOf(aware.getExtensions().getExtraProperties().get("loom_server_install_path"));
        }

        return Constants.SERVER_INSTALL_PATH;
    }

    public static String getClientVersionManifests(@Nullable ExtensionAware aware) {
        if (aware != null && aware.getExtensions().getExtraProperties().has("loom_version_manifests")) {
            return String.valueOf(aware.getExtensions().getExtraProperties().get("loom_version_manifests"));
        }

        return Constants.VERSION_MANIFESTS + "client/" + getOsStringForUrl() + "/version_manifest.json";
    }

    public static String getOsStringForUrl() throws RuntimeException {
        final Platform.OperatingSystem os = Platform.CURRENT.getOperatingSystem();
        if (os.isWindows()) {
            return "win";
        } else if (os.isMacOS()) {
            return "mac";
        } else if (os.isLinux()) {
            return "linux";
        } else {
            throw new RuntimeException("Unsupported operating system: " + os);
        }
    }

    public static String getServerVersionManifests(ExtensionAware aware) {
        if (aware.getExtensions().getExtraProperties().has("loom_server_version_manifests")) {
            return String.valueOf(aware.getExtensions().getExtraProperties().get("loom_server_version_manifests"));
        }

        return Constants.VERSION_MANIFESTS + "server/" + getOsStringForUrl() + "/version_manifest.json";
    }

    public static String getFabricRepository(ExtensionAware aware) {
        if (aware.getExtensions().getExtraProperties().has("loom_fabric_repository")) {
            return String.valueOf(aware.getExtensions().getExtraProperties().get("loom_fabric_repository"));
        }

        return Constants.FABRIC_REPOSITORY;
    }
}

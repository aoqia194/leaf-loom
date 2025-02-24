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
package dev.aoqia.leaf.loom.configuration.providers.zomboid.library;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import dev.aoqia.leaf.loom.configuration.providers.zomboid.ZomboidVersionManifest;
import dev.aoqia.leaf.loom.util.Platform;

/**
 * Utils to get the Zomboid libraries for a given platform, no processing is applied.
 */
public class ZomboidLibraryHelper {
    private static final Pattern NATIVES_PATTERN =
            Pattern.compile("^(?<group>.*)/(.*?)/(?<version>.*)/((?<name>.*?)-(\\k<version>)-)(?<classifier>.*).jar$");

    public static List<Library> getLibrariesForPlatform(ZomboidVersionManifest versionMeta, Platform platform) {
        var libraries = new ArrayList<Library>();

        for (ZomboidVersionManifest.Library library : versionMeta.libraries()) {
            if (!library.isValidForOS(platform)) {
                continue;
            }

//            if (library.artifact() != null) {
                Library mavenLib = Library.fromMaven(library.name(), Library.Target.COMPILE);

                // Versions that have the natives on the classpath, attempt to target them as natives.
                if (mavenLib.classifier() != null && mavenLib.classifier().startsWith("natives-")) {
                    mavenLib = mavenLib.withTarget(Library.Target.NATIVES);
                }

                libraries.add(mavenLib);
//            }

            if (library.hasNativesForOS(platform)) {
                final ZomboidVersionManifest.Download download = library.classifierForOS(platform);

                if (download != null) {
                    libraries.add(downloadToLibrary(download));
                }
            }
        }

        return Collections.unmodifiableList(libraries);
    }

    private static Library downloadToLibrary(ZomboidVersionManifest.Download download) {
        final String path = download.path();
        final Matcher matcher = NATIVES_PATTERN.matcher(path);

        if (!matcher.find()) {
            throw new IllegalStateException("Failed to match regex for natives path : " + path);
        }

        final String group = matcher.group("group").replace("/", ".");
        final String name = matcher.group("name");
        final String version = matcher.group("version");
        final String classifier = matcher.group("classifier");
        final String dependencyNotation = "%s:%s:%s:%s".formatted(group, name, version, classifier);

        return Library.fromMaven(dependencyNotation, Library.Target.NATIVES);
    }
}

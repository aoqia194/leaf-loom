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
package dev.aoqia.leaf.loom.api.mappings.layered;

import java.nio.file.Path;

import dev.aoqia.leaf.loom.configuration.providers.zomboid.ZomboidProvider;
import dev.aoqia.leaf.loom.util.copygamefile.CopyGameFileBuilder;
import dev.aoqia.leaf.loom.util.download.DownloadBuilder;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.MinimalExternalModuleDependency;
import org.gradle.api.logging.Logger;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Experimental /* Very Experimental and not cleanly separated from the impl atm */
public interface MappingContext {
    Path resolveDependency(Dependency dependency);

    Path resolveDependency(MinimalExternalModuleDependency dependency);

    Path resolveMavenDependency(String mavenNotation);

    default String zomboidVersion() {
        return zomboidProvider().clientZomboidVersion();
    }

    ZomboidProvider zomboidProvider();

    /**
     * Creates a temporary working dir to be used to store working files.
     */
    Path workingDirectory(String name);

    Logger getLogger();

    CopyGameFileBuilder copyGameFile(String path);

    DownloadBuilder download(String url);

    boolean refreshDeps();
}

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
package dev.aoqia.loom.configuration.providers.zomboid;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import dev.aoqia.loom.api.mappings.layered.MappingsNamespace;
import dev.aoqia.loom.configuration.ConfigContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MergedZomboidProvider extends ZomboidProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(MergedZomboidProvider.class);
    private Path mergedJar;

    public MergedZomboidProvider(ZomboidMetadataProvider clientMetadataProvider,
        ZomboidMetadataProvider serverMetadataProvider,
        ConfigContext configContext) {
        super(clientMetadataProvider, serverMetadataProvider, configContext);
    }

    @Override
    public void provide() throws Exception {
        super.provide();

        if (!provideServer() || !provideClient()) {
            throw new UnsupportedOperationException(
                "This version does not provide both the client and server jars - please select the client-only or"
                + " server-only jar configuration!");
        }

        if (!Files.exists(mergedJar) || getExtension().refreshDeps()) {
            try {
                mergeJars();
            } catch (Throwable e) {
                Files.deleteIfExists(getZomboidClientJar().toPath());
                Files.deleteIfExists(getZomboidServerJar().toPath());
                Files.deleteIfExists(mergedJar);

                getProject()
                    .getLogger()
                    .error(
                        "Could not merge JARs! Deleting source JARs - please re-run the command and move on.",
                        e);
                throw e;
            }
        }
    }

    @Override
    protected void initFiles() {
        super.initFiles();
        mergedJar = path("zomboid-merged.jar");
    }

    @Override
    public List<Path> getZomboidJars() {
        return List.of(mergedJar);
    }

    @Override
    public MappingsNamespace getOfficialNamespace() {
        return MappingsNamespace.OFFICIAL;
    }

    private void mergeJars() throws IOException {
        File minecraftClientJar = getZomboidClientJar();
        File minecraftServerJar = getZomboidServerJar();

        mergeJars(minecraftClientJar, minecraftServerJar, mergedJar.toFile());
    }

    public static void mergeJars(File clientJar, File serverJar, File mergedJar) throws IOException {
        LOGGER.info(":merging jars");

        Objects.requireNonNull(clientJar, "Cannot merge null client jar?");
        Objects.requireNonNull(serverJar, "Cannot merge null server jar?");

        try (var jarMerger = new ZomboidJarMerger(clientJar, serverJar, mergedJar)) {
            jarMerger.enableSyntheticParamsOffset();
            jarMerger.merge();
        }
    }

    public Path getMergedJar() {
        return mergedJar;
    }
}

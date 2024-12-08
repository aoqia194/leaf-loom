/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021 FabricMC
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

package net.aoqia.loom.configuration.providers.zomboid;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import net.aoqia.loom.api.mappings.layered.MappingsNamespace;
import net.aoqia.loom.configuration.ConfigContext;

public final class SplitZomboidProvider extends ZomboidProvider {
    private Path clientOnlyJar;
    private Path commonJar;

    public SplitZomboidProvider(ZomboidMetadataProvider clientMetadataProvider,
        ZomboidMetadataProvider serverMetadataProvider,
        ConfigContext configContext) {
        super(clientMetadataProvider, serverMetadataProvider, configContext);
    }

    @Override
    public void provide() throws Exception {
        super.provide();

        boolean requiresRefresh =
            getExtension().refreshDeps() || Files.notExists(clientOnlyJar) || Files.notExists(commonJar);

        if (!requiresRefresh) {
            return;
        }

        final Path clientJar = getZomboidClientJar().toPath();
        final Path serverJar = getZomboidServerJar().toPath();

        try (ZomboidJarSplitter jarSplitter = new ZomboidJarSplitter(clientJar, serverJar)) {
            // Required for loader to compute the version info also useful to have in both jars.
            // jarSplitter.sharedEntry("assets/zomboid/lang/en_us.json");
            jarSplitter.split(clientOnlyJar, commonJar);
        } catch (Exception e) {
            Files.deleteIfExists(clientOnlyJar);
            Files.deleteIfExists(commonJar);

            throw new RuntimeException("Failed to split zomboid", e);
        }
    }

    @Override
    protected void initFiles() {
        super.initFiles();

        this.clientOnlyJar = path("zomboid-client-only.jar");
        this.commonJar = path("zomboid-common.jar");
    }

    @Override
    public List<Path> getZomboidJars() {
        return List.of(this.clientOnlyJar, this.commonJar);
    }

    @Override
    public MappingsNamespace getOfficialNamespace() {
        return MappingsNamespace.OFFICIAL;
    }

    public Path getClientOnlyJar() {
        return clientOnlyJar;
    }

    public Path getCommonJar() {
        return commonJar;
    }
}

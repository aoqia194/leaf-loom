/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2022 FabricMC
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

package net.fabricmc.loom.configuration.providers.minecraft;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.configuration.ConfigContext;
import net.fabricmc.tinyremapper.NonClassCopyMode;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;

public abstract sealed class SingleJarZomboidProvider extends ZomboidProvider
        permits SingleJarZomboidProvider.Server, SingleJarZomboidProvider.Client {
    private final MappingsNamespace officialNamespace;
    private Path minecraftEnvOnlyJar;

    private SingleJarZomboidProvider(
            ZomboidMetadataProvider metadataProvider,
            ConfigContext configContext,
            MappingsNamespace officialNamespace) {
        super(metadataProvider, configContext);
        this.officialNamespace = officialNamespace;
    }

    public static SingleJarZomboidProvider.Server server(
            ZomboidMetadataProvider metadataProvider, ConfigContext configContext) {
        return new SingleJarZomboidProvider.Server(
                metadataProvider, configContext, getOfficialNamespace(metadataProvider, true));
    }

    private static MappingsNamespace getOfficialNamespace(ZomboidMetadataProvider metadataProvider, boolean server) {
        return MappingsNamespace.OFFICIAL;
    }

    public static SingleJarZomboidProvider.Client client(
            ZomboidMetadataProvider metadataProvider, ConfigContext configContext) {
        return new SingleJarZomboidProvider.Client(
                metadataProvider, configContext, getOfficialNamespace(metadataProvider, false));
    }

    @Override
    public void provide() throws Exception {
        super.provide();

        // Server only JARs are supported on any version, client only JARs are pretty much useless after 1.3.
        if (provideClient()) {
            getProject().getLogger().warn("Using `clientOnlyZomboidJar()` is not recommended for Zomboid.");
        }

        boolean requiresRefresh = getExtension().refreshDeps() || Files.notExists(minecraftEnvOnlyJar);

        if (!requiresRefresh) {
            return;
        }

        final Path inputJar = getInputJar(this);

        TinyRemapper remapper = null;

        try {
            remapper = TinyRemapper.newRemapper().build();

            Files.deleteIfExists(minecraftEnvOnlyJar);

            // Pass through tiny remapper to fix the meta-inf
            try (OutputConsumerPath outputConsumer = new OutputConsumerPath.Builder(minecraftEnvOnlyJar).build()) {
                outputConsumer.addNonClassFiles(inputJar, NonClassCopyMode.FIX_META_INF, remapper);
                remapper.readInputs(inputJar);
                remapper.apply(outputConsumer);
            }
        } catch (Exception e) {
            Files.deleteIfExists(minecraftEnvOnlyJar);
            throw new RuntimeException("Failed to process %s only jar".formatted(type()), e);
        } finally {
            if (remapper != null) {
                remapper.finish();
            }
        }
    }

    @Override
    protected void initFiles() {
        super.initFiles();

        minecraftEnvOnlyJar = path("minecraft-%s-only.jar".formatted(type()));
    }

    @Override
    public List<Path> getZomboidJars() {
        return List.of(minecraftEnvOnlyJar);
    }

    @Override
    public MappingsNamespace getOfficialNamespace() {
        return officialNamespace;
    }

    abstract SingleJarEnvType type();

    abstract Path getInputJar(SingleJarZomboidProvider provider) throws Exception;

    public Path getMinecraftEnvOnlyJar() {
        return minecraftEnvOnlyJar;
    }

    public static final class Server extends SingleJarZomboidProvider {
        private Server(
                ZomboidMetadataProvider metadataProvider,
                ConfigContext configContext,
                MappingsNamespace officialNamespace) {
            super(metadataProvider, configContext, officialNamespace);
        }

        @Override
        public SingleJarEnvType type() {
            return SingleJarEnvType.SERVER;
        }

        @Override
        public Path getInputJar(SingleJarZomboidProvider provider) {
            return provider.getZomboidServerJar().toPath();
        }

        @Override
        protected boolean provideClient() {
            return false;
        }

        @Override
        protected boolean provideServer() {
            return true;
        }
    }

    public static final class Client extends SingleJarZomboidProvider {
        private Client(
                ZomboidMetadataProvider metadataProvider,
                ConfigContext configContext,
                MappingsNamespace officialNamespace) {
            super(metadataProvider, configContext, officialNamespace);
        }

        @Override
        public SingleJarEnvType type() {
            return SingleJarEnvType.CLIENT;
        }

        @Override
        public Path getInputJar(SingleJarZomboidProvider provider) throws Exception {
            return provider.getZomboidClientJar().toPath();
        }

        @Override
        protected boolean provideClient() {
            return true;
        }

        @Override
        protected boolean provideServer() {
            return false;
        }
    }
}

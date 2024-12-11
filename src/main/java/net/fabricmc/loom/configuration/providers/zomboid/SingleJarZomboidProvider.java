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

package net.aoqia.loom.configuration.providers.zomboid;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import net.aoqia.loom.api.mappings.layered.MappingsNamespace;
import net.aoqia.loom.configuration.ConfigContext;
import net.fabricmc.tinyremapper.NonClassCopyMode;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;

public abstract sealed class SingleJarZomboidProvider extends ZomboidProvider
    permits SingleJarZomboidProvider.Server, SingleJarZomboidProvider.Client {
    private final MappingsNamespace officialNamespace;
    private Path zomboidEnvOnlyJar;

    private SingleJarZomboidProvider(
        ZomboidMetadataProvider clientMetadataProvider,
        ZomboidMetadataProvider serverMetadataProvider,
        ConfigContext configContext,
        MappingsNamespace officialNamespace) {
        super(clientMetadataProvider, serverMetadataProvider, configContext);
        this.officialNamespace = officialNamespace;
    }

    public static SingleJarZomboidProvider.Server server(
        ZomboidMetadataProvider clientMetadataProvider,
        ZomboidMetadataProvider serverMetadataProvider,
        ConfigContext configContext) {
        return new SingleJarZomboidProvider.Server(
            clientMetadataProvider,
            serverMetadataProvider,
            configContext);
    }

    public static SingleJarZomboidProvider.Client client(
        ZomboidMetadataProvider clientMetadataProvider,
        ZomboidMetadataProvider serverMetadataProvider, ConfigContext configContext) {
        return new SingleJarZomboidProvider.Client(
            clientMetadataProvider, serverMetadataProvider, configContext);
    }

    @Override
    public void provide() throws Exception {
        super.provide();

        // Unlike Minecraft, PZ is the opposite. Server JARs are basically useless for ANY version <=41.78.16.
        if (provideServer()) {
            getProject().getLogger().warn("Using `serverOnlyZomboidJar()` is not recommended for Zomboid.");
        }

        boolean requiresRefresh = getExtension().refreshDeps() || Files.notExists(zomboidEnvOnlyJar);
        if (!requiresRefresh) {
            return;
        }

        final Path inputJar = getInputJar(this);

        TinyRemapper remapper = null;

        try {
            remapper = TinyRemapper.newRemapper().build();

            Files.deleteIfExists(zomboidEnvOnlyJar);

            // Pass through tiny remapper to fix the meta-inf
            try (OutputConsumerPath outputConsumer = new OutputConsumerPath.Builder(zomboidEnvOnlyJar).build()) {
                outputConsumer.addNonClassFiles(inputJar, NonClassCopyMode.FIX_META_INF, remapper);
                remapper.readInputs(inputJar);
                remapper.apply(outputConsumer);
            }
        } catch (Exception e) {
            Files.deleteIfExists(zomboidEnvOnlyJar);
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

        zomboidEnvOnlyJar = path("zomboid-%s-only.jar".formatted(type()));
    }

    @Override
    public List<Path> getZomboidJars() {
        return List.of(zomboidEnvOnlyJar);
    }

    @Override
    public MappingsNamespace getOfficialNamespace() {
        return officialNamespace;
    }

    abstract SingleJarEnvType type();

    abstract Path getInputJar(SingleJarZomboidProvider provider) throws Exception;

    public Path getZomboidEnvOnlyJar() {
        return zomboidEnvOnlyJar;
    }

    public static final class Server extends SingleJarZomboidProvider {
        private Server(
            ZomboidMetadataProvider clientMetadataProvider,
            ZomboidMetadataProvider serverMetadataProvider,
            ConfigContext configContext) {
            super(clientMetadataProvider, serverMetadataProvider, configContext, MappingsNamespace.OFFICIAL);
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
            ZomboidMetadataProvider clientMetadataProvider,
            ZomboidMetadataProvider serverMetadataProvider,
            ConfigContext configContext) {
            super(clientMetadataProvider, serverMetadataProvider, configContext, MappingsNamespace.OFFICIAL);
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

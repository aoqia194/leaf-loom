/*
 * This file is part of leaf-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2022 aoqia, FabricMC
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
package dev.aoqia.leaf.loom.configuration.providers.zomboid;

import java.util.List;

import dev.aoqia.leaf.loom.LoomGradleExtension;
import dev.aoqia.leaf.loom.configuration.ConfigContext;
import dev.aoqia.leaf.loom.configuration.decompile.DecompileConfiguration;
import dev.aoqia.leaf.loom.configuration.decompile.SingleJarDecompileConfiguration;
import dev.aoqia.leaf.loom.configuration.decompile.SplitDecompileConfiguration;
import dev.aoqia.leaf.loom.configuration.processors.ZomboidJarProcessorManager;
import dev.aoqia.leaf.loom.configuration.providers.zomboid.mapped.MappedZomboidProvider;
import dev.aoqia.leaf.loom.configuration.providers.zomboid.mapped.NamedZomboidProvider;
import dev.aoqia.leaf.loom.configuration.providers.zomboid.mapped.ProcessedNamedZomboidProvider;
import org.gradle.api.Project;

public record ZomboidJarConfiguration<
    M extends ZomboidProvider,
    N extends NamedZomboidProvider<M>,
    Q extends MappedZomboidProvider>(
    ZomboidProviderFactory<M> ZomboidProviderFactory,
    NamedZomboidProviderFactory<M> namedZomboidProviderFactory,
    ProcessedNamedZomboidProviderFactory<M, N> processedNamedZomboidProviderFactory,
    DecompileConfigurationFactory<Q> decompileConfigurationFactory,
    List<String> supportedEnvironments) {
    public static final ZomboidJarConfiguration<
        SingleJarZomboidProvider,
        NamedZomboidProvider.SingleJarImpl,
        MappedZomboidProvider> SERVER_ONLY = new ZomboidJarConfiguration<>(
        SingleJarZomboidProvider::server,
        NamedZomboidProvider.SingleJarImpl::server,
        ProcessedNamedZomboidProvider.SingleJarImpl::server,
        SingleJarDecompileConfiguration::new,
        List.of("server")
    );
    public static final ZomboidJarConfiguration<
        SingleJarZomboidProvider,
        NamedZomboidProvider.SingleJarImpl,
        MappedZomboidProvider> CLIENT_ONLY = new ZomboidJarConfiguration<>(
        SingleJarZomboidProvider::client,
        NamedZomboidProvider.SingleJarImpl::client,
        ProcessedNamedZomboidProvider.SingleJarImpl::client,
        SingleJarDecompileConfiguration::new,
        List.of("client")
    );
    public static final ZomboidJarConfiguration<
        SplitZomboidProvider,
        NamedZomboidProvider.SplitImpl,
        MappedZomboidProvider.Split> SPLIT = new ZomboidJarConfiguration<>(
        SplitZomboidProvider::new,
        NamedZomboidProvider.SplitImpl::new,
        ProcessedNamedZomboidProvider.SplitImpl::new,
        SplitDecompileConfiguration::new,
        List.of("client", "server")
    );

    public ZomboidProvider createZomboidProvider(ZomboidMetadataProvider clientMetadataProvider,
        ZomboidMetadataProvider serverMetadataProvider,
        ConfigContext context) {
        return ZomboidProviderFactory.create(clientMetadataProvider, serverMetadataProvider, context);
    }

    public NamedZomboidProvider<M> createNamedZomboidProvider(Project project) {
        return namedZomboidProviderFactory.create(project, getZomboidProvider(project));
    }

    private M getZomboidProvider(Project project) {
        LoomGradleExtension extension = LoomGradleExtension.get(project);
        //noinspection unchecked
        return (M) extension.getZomboidProvider();
    }

    public ProcessedNamedZomboidProvider<M, N> createProcessedNamedZomboidProvider(NamedZomboidProvider<?> namedZomboidProvider,
        ZomboidJarProcessorManager jarProcessorManager) {
        return processedNamedZomboidProviderFactory.create((N) namedZomboidProvider, jarProcessorManager);
    }

    public DecompileConfiguration<Q> createDecompileConfiguration(Project project) {
        return decompileConfigurationFactory.create(project, getMappedZomboidProvider(project));
    }

    private Q getMappedZomboidProvider(Project project) {
        LoomGradleExtension extension = LoomGradleExtension.get(project);
        //noinspection unchecked
        return (Q) extension.getNamedZomboidProvider();
    }

    // Factory interfaces:
    private interface ZomboidProviderFactory<M extends ZomboidProvider> {
        M create(ZomboidMetadataProvider clientMetadataProvider,
            ZomboidMetadataProvider serverMetadataProvider,
            ConfigContext configContext);
    }

    private interface NamedZomboidProviderFactory<M extends ZomboidProvider> {
        NamedZomboidProvider<M> create(Project project, M ZomboidProvider);
    }

    private interface ProcessedNamedZomboidProviderFactory<M extends ZomboidProvider,
        N extends NamedZomboidProvider<M>> {
        ProcessedNamedZomboidProvider<M, N> create(N namedZomboidProvider,
            ZomboidJarProcessorManager jarProcessorManager);
    }

    private interface DecompileConfigurationFactory<M extends MappedZomboidProvider> {
        DecompileConfiguration<M> create(Project project, M ZomboidProvider);
    }
}

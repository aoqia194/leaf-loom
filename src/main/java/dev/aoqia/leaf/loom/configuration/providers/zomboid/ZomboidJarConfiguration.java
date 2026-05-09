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

package dev.aoqia.leaf.loom.configuration.providers.zomboid;

import java.util.List;

import org.gradle.api.Project;

import dev.aoqia.leaf.loom.LoomGradleExtension;
import dev.aoqia.leaf.loom.configuration.ConfigContext;
import dev.aoqia.leaf.loom.configuration.decompile.DecompileConfiguration;
import dev.aoqia.leaf.loom.configuration.decompile.SingleJarDecompileConfiguration;
import dev.aoqia.leaf.loom.configuration.decompile.SplitDecompileConfiguration;
import dev.aoqia.leaf.loom.configuration.processors.ZomboidJarProcessorManager;
import dev.aoqia.leaf.loom.configuration.providers.zomboid.mapped.IntermediaryZomboidProvider;
import dev.aoqia.leaf.loom.configuration.providers.zomboid.mapped.MappedZomboidProvider;
import dev.aoqia.leaf.loom.configuration.providers.zomboid.mapped.NamedZomboidProvider;
import dev.aoqia.leaf.loom.configuration.providers.zomboid.mapped.ProcessedNamedZomboidProvider;

public record ZomboidJarConfiguration<
		M extends ZomboidProvider,
		N extends NamedZomboidProvider<M>,
		Q extends MappedZomboidProvider>(
				MinecraftProviderFactory<M> minecraftProviderFactory,
				IntermediaryMinecraftProviderFactory<M> intermediaryMinecraftProviderFactory,
				NamedMinecraftProviderFactory<M> namedMinecraftProviderFactory,
				ProcessedNamedMinecraftProviderFactory<M, N> processedNamedMinecraftProviderFactory,
				DecompileConfigurationFactory<Q> decompileConfigurationFactory,
				List<String> supportedEnvironments) {
	public static final ZomboidJarConfiguration<
			MergedZomboidProvider,
				NamedZomboidProvider.MergedImpl,
			MappedZomboidProvider> MERGED = new ZomboidJarConfiguration<>(
				MergedZomboidProvider::new,
				IntermediaryZomboidProvider.MergedImpl::new,
				NamedZomboidProvider.MergedImpl::new,
				ProcessedNamedZomboidProvider.MergedImpl::new,
				SingleJarDecompileConfiguration::new,
				List.of("client", "server")
			);
	public static final ZomboidJarConfiguration<
			LegacyMergedZomboidProvider,
				NamedZomboidProvider.LegacyMergedImpl,
			MappedZomboidProvider> LEGACY_MERGED = new ZomboidJarConfiguration<>(
				LegacyMergedZomboidProvider::new,
				IntermediaryZomboidProvider.LegacyMergedImpl::new,
				NamedZomboidProvider.LegacyMergedImpl::new,
				ProcessedNamedZomboidProvider.LegacyMergedImpl::new,
				SingleJarDecompileConfiguration::new,
				List.of("client", "server")
			);
	public static final ZomboidJarConfiguration<
			SingleJarZomboidProvider,
				NamedZomboidProvider.SingleJarImpl,
			MappedZomboidProvider> SERVER_ONLY = new ZomboidJarConfiguration<>(
				SingleJarZomboidProvider::server,
				IntermediaryZomboidProvider.SingleJarImpl::server,
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
				IntermediaryZomboidProvider.SingleJarImpl::client,
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
				IntermediaryZomboidProvider.SplitImpl::new,
				NamedZomboidProvider.SplitImpl::new,
				ProcessedNamedZomboidProvider.SplitImpl::new,
				SplitDecompileConfiguration::new,
				List.of("client", "server")
			);

	public ZomboidProvider createMinecraftProvider(ZomboidMetadataProvider metadataProvider, ConfigContext context) {
		return minecraftProviderFactory.create(metadataProvider, context);
	}

	public IntermediaryZomboidProvider<M> createIntermediaryMinecraftProvider(Project project) {
		return intermediaryMinecraftProviderFactory.create(project, getMinecraftProvider(project));
	}

	public NamedZomboidProvider<M> createNamedMinecraftProvider(Project project) {
		return namedMinecraftProviderFactory.create(project, getMinecraftProvider(project));
	}

	public ProcessedNamedZomboidProvider<M, N> createProcessedNamedMinecraftProvider(NamedZomboidProvider<?> namedMinecraftProvider, ZomboidJarProcessorManager jarProcessorManager) {
		return processedNamedMinecraftProviderFactory.create((N) namedMinecraftProvider, jarProcessorManager);
	}

	public DecompileConfiguration<Q> createDecompileConfiguration(Project project) {
		return decompileConfigurationFactory.create(project, getMappedMinecraftProvider(project));
	}

	private M getMinecraftProvider(Project project) {
		LoomGradleExtension extension = LoomGradleExtension.get(project);
		//noinspection unchecked
		return (M) extension.getMinecraftProvider();
	}

	private Q getMappedMinecraftProvider(Project project) {
		LoomGradleExtension extension = LoomGradleExtension.get(project);
		//noinspection unchecked
		return (Q) extension.getNamedMinecraftProvider();
	}

	// Factory interfaces:
	private interface MinecraftProviderFactory<M extends ZomboidProvider> {
		M create(ZomboidMetadataProvider metadataProvider, ConfigContext configContext);
	}

	private interface IntermediaryMinecraftProviderFactory<M extends ZomboidProvider> {
		IntermediaryZomboidProvider<M> create(Project project, M minecraftProvider);
	}

	private interface NamedMinecraftProviderFactory<M extends ZomboidProvider> {
		NamedZomboidProvider<M> create(Project project, M minecraftProvider);
	}

	private interface ProcessedNamedMinecraftProviderFactory<M extends ZomboidProvider, N extends NamedZomboidProvider<M>> {
		ProcessedNamedZomboidProvider<M, N> create(N namedMinecraftProvider, ZomboidJarProcessorManager jarProcessorManager);
	}

	private interface DecompileConfigurationFactory<M extends MappedZomboidProvider> {
		DecompileConfiguration<M> create(Project project, M minecraftProvider);
	}
}

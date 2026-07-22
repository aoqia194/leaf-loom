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

package dev.aoqia.leaf.loom.configuration.providers.zomboid.mapped;

import java.util.List;

import dev.aoqia.leaf.loom.configuration.providers.zomboid.CompleteJarZomboidProvider;

import org.gradle.api.Project;

import dev.aoqia.leaf.loom.api.mappings.layered.MappingsNamespace;
import dev.aoqia.leaf.loom.configuration.providers.zomboid.LegacyMergedZomboidProvider;
import dev.aoqia.leaf.loom.configuration.providers.zomboid.MergedZomboidProvider;
import dev.aoqia.leaf.loom.configuration.providers.zomboid.ZomboidJar;
import dev.aoqia.leaf.loom.configuration.providers.zomboid.ZomboidProvider;
import dev.aoqia.leaf.loom.configuration.providers.zomboid.SingleJarEnvType;
import dev.aoqia.leaf.loom.configuration.providers.zomboid.SingleJarZomboidProvider;
import dev.aoqia.leaf.loom.configuration.providers.zomboid.SplitZomboidProvider;
import net.fabricmc.tinyremapper.TinyRemapper;

public abstract sealed class IntermediaryZomboidProvider<M extends ZomboidProvider> extends AbstractMappedZomboidProvider<M> permits IntermediaryZomboidProvider.MergedImpl, IntermediaryZomboidProvider.LegacyMergedImpl, IntermediaryZomboidProvider.SingleJarImpl, IntermediaryZomboidProvider.SplitImpl, IntermediaryZomboidProvider.CompleteJarImpl {
	public IntermediaryZomboidProvider(Project project, M minecraftProvider) {
		super(project, minecraftProvider);

		if (extension.disableObfuscation()) {
			throw new UnsupportedOperationException("Intermediary Minecraft providers cannot be used when obfuscation is disabled");
		}
	}

	@Override
	public final MappingsNamespace getTargetNamespace() {
		return MappingsNamespace.INTERMEDIARY;
	}

	@Override
	public MavenScope getMavenScope() {
		return MavenScope.GLOBAL;
	}

	@Override
	protected boolean requiresBackupJars() {
		// No backup jars should be created for intermediary providers, as we never decompile the intermediary jars.
		return false;
	}

	public static final class MergedImpl extends IntermediaryZomboidProvider<MergedZomboidProvider> implements Merged {
		public MergedImpl(Project project, MergedZomboidProvider minecraftProvider) {
			super(project, minecraftProvider);
		}

		@Override
		public List<RemappedJars> getRemappedJars() {
			return List.of(
				new RemappedJars(zomboidProvider.getMergedJar(), getMergedJar(), zomboidProvider.getOfficialNamespace())
			);
		}
	}

	public static final class LegacyMergedImpl extends IntermediaryZomboidProvider<LegacyMergedZomboidProvider> implements Merged {
		private final SingleJarImpl server;
		private final SingleJarImpl client;

		public LegacyMergedImpl(Project project, LegacyMergedZomboidProvider minecraftProvider) {
			super(project, minecraftProvider);
			server = new SingleJarImpl(project, minecraftProvider.getServerZomboidProvider(), SingleJarEnvType.SERVER);
			client = new SingleJarImpl(project, minecraftProvider.getClientZomboidProvider(), SingleJarEnvType.CLIENT);
		}

		@Override
		public List<ZomboidJar> provide(ProvideContext context) throws Exception {
			final List<ZomboidJar> minecraftJars = List.of(getMergedJar());

			// this check must be done before the client and server impls are provided
			// because the merging only needs to happen if the remapping step is run
			final boolean refreshOutputs = client.shouldRefreshOutputs(context)
					|| server.shouldRefreshOutputs(context)
					|| this.shouldRefreshOutputs(context);

			// Map the client and server jars separately
			server.provide(context);
			client.provide(context);

			if (refreshOutputs) {
				// then merge them
				MergedZomboidProvider.mergeJars(
							client.getEnvOnlyJar().toFile(),
							server.getEnvOnlyJar().toFile(),
							getMergedJar().toFile()
				);

				createBackupJars(minecraftJars);
			}

			return minecraftJars;
		}

		@Override
		public List<RemappedJars> getRemappedJars() {
			// The delegate providers will handle the remapping
			throw new UnsupportedOperationException("LegacyMergedImpl does not support getRemappedJars");
		}

		@Override
		public List<? extends OutputJar> getOutputJars() {
			return List.of(
				new SimpleOutputJar(getMergedJar())
			);
		}

		@Override
		public List<ZomboidJar.Type> getDependencyTypes() {
			return List.of(ZomboidJar.Type.MERGED);
		}
	}

	public static final class SplitImpl extends IntermediaryZomboidProvider<SplitZomboidProvider> implements Split {
		public SplitImpl(Project project, SplitZomboidProvider minecraftProvider) {
			super(project, minecraftProvider);
		}

		@Override
		public List<RemappedJars> getRemappedJars() {
			return List.of(
				new RemappedJars(zomboidProvider.getZomboidCommonJar(), getCommonJar(), zomboidProvider.getOfficialNamespace()),
				new RemappedJars(zomboidProvider.getZomboidClientOnlyJar(), getClientOnlyJar(), zomboidProvider.getOfficialNamespace(), zomboidProvider.getZomboidCommonJar())
			);
		}

		@Override
		protected void configureRemapper(RemappedJars remappedJars, TinyRemapper.Builder tinyRemapperBuilder) {
			configureSplitRemapper(remappedJars, tinyRemapperBuilder);
		}
	}

	public static final class SingleJarImpl extends IntermediaryZomboidProvider<SingleJarZomboidProvider> implements SingleJar {
		private final SingleJarEnvType env;

		private SingleJarImpl(Project project, SingleJarZomboidProvider minecraftProvider, SingleJarEnvType env) {
			super(project, minecraftProvider);
			this.env = env;
		}

		public static SingleJarImpl server(Project project, SingleJarZomboidProvider minecraftProvider) {
			return new SingleJarImpl(project, minecraftProvider, SingleJarEnvType.SERVER);
		}

		public static SingleJarImpl client(Project project, SingleJarZomboidProvider minecraftProvider) {
			return new SingleJarImpl(project, minecraftProvider, SingleJarEnvType.CLIENT);
		}

		@Override
		public List<RemappedJars> getRemappedJars() {
			return List.of(
				new RemappedJars(zomboidProvider.getZomboidEnvOnlyJar(), getEnvOnlyJar(), zomboidProvider.getOfficialNamespace())
			);
		}

		@Override
		public SingleJarEnvType env() {
			return env;
		}
	}

    public static final class CompleteJarImpl extends IntermediaryZomboidProvider<CompleteJarZomboidProvider> implements CompleteJar {
        public CompleteJarImpl(Project project, CompleteJarZomboidProvider provider) {
            super(project, provider);
        }

        @Override
        public List<RemappedJars> getRemappedJars() {
            return List.of(
                new RemappedJars(zomboidProvider.getZomboidJar(), getZomboidJar(), zomboidProvider.getOfficialNamespace())
            );
        }
    }
}

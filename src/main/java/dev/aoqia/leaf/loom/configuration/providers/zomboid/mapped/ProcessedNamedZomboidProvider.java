/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021-2023 FabricMC
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import dev.aoqia.leaf.loom.configuration.ConfigContext;
import dev.aoqia.leaf.loom.configuration.mods.dependency.LocalMavenHelper;
import dev.aoqia.leaf.loom.configuration.processors.ZomboidJarProcessorManager;
import dev.aoqia.leaf.loom.configuration.processors.ProcessorContextImpl;
import dev.aoqia.leaf.loom.configuration.providers.zomboid.LegacyMergedZomboidProvider;
import dev.aoqia.leaf.loom.configuration.providers.zomboid.MergedZomboidProvider;
import dev.aoqia.leaf.loom.configuration.providers.zomboid.ZomboidJar;
import dev.aoqia.leaf.loom.configuration.providers.zomboid.ZomboidProvider;
import dev.aoqia.leaf.loom.configuration.providers.zomboid.ZomboidSourceSets;
import dev.aoqia.leaf.loom.configuration.providers.zomboid.SingleJarEnvType;
import dev.aoqia.leaf.loom.configuration.providers.zomboid.SingleJarZomboidProvider;
import dev.aoqia.leaf.loom.configuration.providers.zomboid.SplitZomboidProvider;

public abstract class ProcessedNamedZomboidProvider<M extends ZomboidProvider, P extends NamedZomboidProvider<M>> extends NamedZomboidProvider<M> {
	private final P parentMinecraftProvider;
	private final ZomboidJarProcessorManager jarProcessorManager;

	public ProcessedNamedZomboidProvider(P parentMinecraftProvide, ZomboidJarProcessorManager jarProcessorManager) {
		super(parentMinecraftProvide.getProject(), parentMinecraftProvide.getMinecraftProvider());
		this.parentMinecraftProvider = parentMinecraftProvide;
		this.jarProcessorManager = Objects.requireNonNull(jarProcessorManager);
	}

	@Override
	public List<ZomboidJar> provide(ProvideContext context) throws Exception {
		final List<ZomboidJar> parentMinecraftJars = parentMinecraftProvider.getMinecraftJars();
		final Map<ZomboidJar, ZomboidJar> minecraftJarOutputMap = parentMinecraftJars.stream()
				.collect(Collectors.toMap(Function.identity(), this::getProcessedJar));
		final List<ZomboidJar> minecraftJars = List.copyOf(minecraftJarOutputMap.values());

		parentMinecraftProvider.provide(context.withApplyDependencies(false));

		boolean requiresProcessing = shouldRefreshOutputs(context) || parentMinecraftJars.stream()
				.map(this::getProcessedPath)
				.anyMatch(jarProcessorManager::requiresProcessingJar);

		if (requiresProcessing) {
			processJars(minecraftJarOutputMap, context.configContext());
			createBackupJars(minecraftJars);
		}

		if (context.applyDependencies()) {
			applyDependencies();
		}

		return List.copyOf(minecraftJarOutputMap.values());
	}

	@Override
	public List<? extends OutputJar> getOutputJars() {
		return parentMinecraftProvider.getMinecraftJars().stream()
				.map(this::getProcessedJar)
				.map(SimpleOutputJar::new)
				.toList();
	}

	@Override
	public MavenScope getMavenScope() {
		return MavenScope.LOCAL;
	}

	private void processJars(Map<ZomboidJar, ZomboidJar> minecraftJarMap, ConfigContext configContext) throws IOException {
		for (Map.Entry<ZomboidJar, ZomboidJar> entry : minecraftJarMap.entrySet()) {
			final ZomboidJar minecraftJar = entry.getKey();
			final ZomboidJar outputJar = entry.getValue();
			deleteSimilarJars(outputJar.getPath());

			final LocalMavenHelper mavenHelper = getMavenHelper(minecraftJar.getType());
			final Path outputPath = mavenHelper.copyToMaven(minecraftJar.getPath(), null);

			assert outputJar.getPath().equals(outputPath);

			jarProcessorManager.processJar(outputPath, new ProcessorContextImpl(configContext, minecraftJar));
		}
	}

	@Override
	public List<ZomboidJar.Type> getDependencyTypes() {
		return parentMinecraftProvider.getDependencyTypes();
	}

	private void applyDependencies() {
		final List<ZomboidJar.Type> dependencyTargets = getDependencyTypes();

		if (dependencyTargets.isEmpty()) {
			return;
		}

		ZomboidSourceSets.get(getProject()).applyDependencies(
				(configuration, name) -> getProject().getDependencies().add(configuration, getDependencyNotation(name)),
				dependencyTargets
		);
	}

	private void deleteSimilarJars(Path jar) throws IOException {
		Files.deleteIfExists(jar);
		final Path parent = jar.getParent();

		if (Files.notExists(parent)) {
			return;
		}

		for (Path path : Files.list(parent).filter(Files::isRegularFile)
				.filter(path -> path.getFileName().startsWith(jar.getFileName().toString().replace(".jar", ""))).toList()) {
			Files.deleteIfExists(path);
		}
	}

	@Override
	protected String getName(ZomboidJar.Type type) {
		// Hash the cache value so that we don't have to process the same JAR multiple times for many projects
		return "minecraft-%s-%s".formatted(type.toString(), jarProcessorManager.getJarHash());
	}

	@Override
	public Path getJar(ZomboidJar.Type type) {
		// Something has gone wrong if this gets called.
		throw new UnsupportedOperationException();
	}

	@Override
	public List<RemappedJars> getRemappedJars() {
		throw new UnsupportedOperationException();
	}

	@Override
	public List<ZomboidJar> getMinecraftJars() {
		return getParentMinecraftProvider().getMinecraftJars().stream()
				.map(this::getProcessedJar)
				.toList();
	}

	public P getParentMinecraftProvider() {
		return parentMinecraftProvider;
	}

	private Path getProcessedPath(ZomboidJar minecraftJar) {
		final LocalMavenHelper mavenHelper = getMavenHelper(minecraftJar.getType());
		return mavenHelper.getOutputFile(null);
	}

	public ZomboidJar getProcessedJar(ZomboidJar minecraftJar) {
		return minecraftJar.forPath(getProcessedPath(minecraftJar));
	}

	public static final class MergedImpl extends ProcessedNamedZomboidProvider<MergedZomboidProvider, NamedZomboidProvider.MergedImpl> implements Merged {
		public MergedImpl(NamedZomboidProvider.MergedImpl parentMinecraftProvide, ZomboidJarProcessorManager jarProcessorManager) {
			super(parentMinecraftProvide, jarProcessorManager);
		}

		@Override
		public ZomboidJar getMergedJar() {
			return getProcessedJar(getParentMinecraftProvider().getMergedJar());
		}
	}

	public static final class LegacyMergedImpl extends ProcessedNamedZomboidProvider<LegacyMergedZomboidProvider, NamedZomboidProvider.LegacyMergedImpl> implements Merged {
		public LegacyMergedImpl(NamedZomboidProvider.LegacyMergedImpl parentMinecraftProvider, ZomboidJarProcessorManager jarProcessorManager) {
			super(parentMinecraftProvider, jarProcessorManager);
		}

		@Override
		public ZomboidJar getMergedJar() {
			return getProcessedJar(getParentMinecraftProvider().getMergedJar());
		}
	}

	public static final class SplitImpl extends ProcessedNamedZomboidProvider<SplitZomboidProvider, NamedZomboidProvider.SplitImpl> implements Split {
		public SplitImpl(NamedZomboidProvider.SplitImpl parentMinecraftProvide, ZomboidJarProcessorManager jarProcessorManager) {
			super(parentMinecraftProvide, jarProcessorManager);
		}

		@Override
		public ZomboidJar getCommonJar() {
			return getProcessedJar(getParentMinecraftProvider().getCommonJar());
		}

		@Override
		public ZomboidJar getClientOnlyJar() {
			return getProcessedJar(getParentMinecraftProvider().getClientOnlyJar());
		}
	}

	public static final class SingleJarImpl extends ProcessedNamedZomboidProvider<SingleJarZomboidProvider, NamedZomboidProvider.SingleJarImpl> implements SingleJar {
		private final SingleJarEnvType env;

		private SingleJarImpl(NamedZomboidProvider.SingleJarImpl parentMinecraftProvide, ZomboidJarProcessorManager jarProcessorManager, SingleJarEnvType env) {
			super(parentMinecraftProvide, jarProcessorManager);
			this.env = env;
		}

		public static ProcessedNamedZomboidProvider.SingleJarImpl server(NamedZomboidProvider.SingleJarImpl parentMinecraftProvide, ZomboidJarProcessorManager jarProcessorManager) {
			return new ProcessedNamedZomboidProvider.SingleJarImpl(parentMinecraftProvide, jarProcessorManager, SingleJarEnvType.SERVER);
		}

		public static ProcessedNamedZomboidProvider.SingleJarImpl client(NamedZomboidProvider.SingleJarImpl parentMinecraftProvide, ZomboidJarProcessorManager jarProcessorManager) {
			return new ProcessedNamedZomboidProvider.SingleJarImpl(parentMinecraftProvide, jarProcessorManager, SingleJarEnvType.CLIENT);
		}

		@Override
		public ZomboidJar getEnvOnlyJar() {
			return getProcessedJar(getParentMinecraftProvider().getEnvOnlyJar());
		}

		@Override
		public SingleJarEnvType env() {
			return env;
		}
	}
}

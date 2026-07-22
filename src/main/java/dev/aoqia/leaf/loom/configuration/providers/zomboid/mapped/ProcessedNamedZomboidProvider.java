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
import dev.aoqia.leaf.loom.configuration.providers.zomboid.CompleteJarZomboidProvider;
import dev.aoqia.leaf.loom.configuration.providers.zomboid.LegacyMergedZomboidProvider;
import dev.aoqia.leaf.loom.configuration.providers.zomboid.MergedZomboidProvider;
import dev.aoqia.leaf.loom.configuration.providers.zomboid.ZomboidJar;
import dev.aoqia.leaf.loom.configuration.providers.zomboid.ZomboidProvider;
import dev.aoqia.leaf.loom.configuration.providers.zomboid.ZomboidSourceSets;
import dev.aoqia.leaf.loom.configuration.providers.zomboid.SingleJarEnvType;
import dev.aoqia.leaf.loom.configuration.providers.zomboid.SingleJarZomboidProvider;
import dev.aoqia.leaf.loom.configuration.providers.zomboid.SplitZomboidProvider;

public abstract class ProcessedNamedZomboidProvider<M extends ZomboidProvider, P extends NamedZomboidProvider<M>> extends NamedZomboidProvider<M> {
	private final P parentProvider;
	private final ZomboidJarProcessorManager jarProcessorManager;

	public ProcessedNamedZomboidProvider(P parentProvide, ZomboidJarProcessorManager jarProcessorManager) {
		super(parentProvide.getProject(), parentProvide.getZomboidProvider());
		this.parentProvider = parentProvide;
		this.jarProcessorManager = Objects.requireNonNull(jarProcessorManager);
	}

	@Override
	public List<ZomboidJar> provide(ProvideContext context) throws Exception {
		final List<ZomboidJar> parentJars = parentProvider.getZomboidJars();
		final Map<ZomboidJar, ZomboidJar> jarOutputMap = parentJars.stream()
				.collect(Collectors.toMap(Function.identity(), this::getProcessedJar));
		final List<ZomboidJar> zomboidJars = List.copyOf(jarOutputMap.values());

		parentProvider.provide(context.withApplyDependencies(false));

		boolean requiresProcessing = shouldRefreshOutputs(context) || parentJars.stream()
				.map(this::getProcessedPath)
				.anyMatch(jarProcessorManager::requiresProcessingJar);

		if (requiresProcessing) {
			processJars(jarOutputMap, context.configContext());
			createBackupJars(zomboidJars);
		}

		if (context.applyDependencies()) {
			applyDependencies();
		}

		return List.copyOf(jarOutputMap.values());
	}

	@Override
	public List<? extends OutputJar> getOutputJars() {
		return parentProvider.getZomboidJars().stream()
				.map(this::getProcessedJar)
				.map(SimpleOutputJar::new)
				.toList();
	}

	@Override
	public MavenScope getMavenScope() {
		return MavenScope.LOCAL;
	}

	private void processJars(Map<ZomboidJar, ZomboidJar> zomboidJarMap, ConfigContext configContext) throws IOException {
		for (Map.Entry<ZomboidJar, ZomboidJar> entry : zomboidJarMap.entrySet()) {
			final ZomboidJar zomboidJar = entry.getKey();
			final ZomboidJar outputJar = entry.getValue();
			deleteSimilarJars(outputJar.getPath());

			final LocalMavenHelper mavenHelper = getMavenHelper(zomboidJar.getType());
			final Path outputPath = mavenHelper.copyToMaven(zomboidJar.getPath(), null);

			assert outputJar.getPath().equals(outputPath);

			jarProcessorManager.processJar(outputPath, new ProcessorContextImpl(configContext, zomboidJar));
		}
	}

	@Override
	public List<ZomboidJar.Type> getDependencyTypes() {
		return parentProvider.getDependencyTypes();
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
		return "zomboid-%s-%s".formatted(type.toString(), jarProcessorManager.getJarHash());
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
	public List<ZomboidJar> getZomboidJars() {
		return getParentProvider().getZomboidJars().stream()
				.map(this::getProcessedJar)
				.toList();
	}

	public P getParentProvider() {
		return parentProvider;
	}

	private Path getProcessedPath(ZomboidJar jar) {
		final LocalMavenHelper mavenHelper = getMavenHelper(jar.getType());
		return mavenHelper.getOutputFile(null);
	}

	public ZomboidJar getProcessedJar(ZomboidJar jar) {
		return jar.forPath(getProcessedPath(jar));
	}

	public static final class MergedImpl extends ProcessedNamedZomboidProvider<MergedZomboidProvider, NamedZomboidProvider.MergedImpl> implements Merged {
		public MergedImpl(NamedZomboidProvider.MergedImpl parentProvide, ZomboidJarProcessorManager jarProcessorManager) {
			super(parentProvide, jarProcessorManager);
		}

		@Override
		public ZomboidJar getMergedJar() {
			return getProcessedJar(getParentProvider().getMergedJar());
		}
	}

	public static final class LegacyMergedImpl extends ProcessedNamedZomboidProvider<LegacyMergedZomboidProvider, NamedZomboidProvider.LegacyMergedImpl> implements Merged {
		public LegacyMergedImpl(NamedZomboidProvider.LegacyMergedImpl parentProvider, ZomboidJarProcessorManager jarProcessorManager) {
			super(parentProvider, jarProcessorManager);
		}

		@Override
		public ZomboidJar getMergedJar() {
			return getProcessedJar(getParentProvider().getMergedJar());
		}
	}

	public static final class SplitImpl extends ProcessedNamedZomboidProvider<SplitZomboidProvider, NamedZomboidProvider.SplitImpl> implements Split {
		public SplitImpl(NamedZomboidProvider.SplitImpl parentProvide, ZomboidJarProcessorManager jarProcessorManager) {
			super(parentProvide, jarProcessorManager);
		}

		@Override
		public ZomboidJar getCommonJar() {
			return getProcessedJar(getParentProvider().getCommonJar());
		}

		@Override
		public ZomboidJar getClientOnlyJar() {
			return getProcessedJar(getParentProvider().getClientOnlyJar());
		}
	}

	public static final class SingleJarImpl extends ProcessedNamedZomboidProvider<SingleJarZomboidProvider, NamedZomboidProvider.SingleJarImpl> implements SingleJar {
		private final SingleJarEnvType env;

		private SingleJarImpl(NamedZomboidProvider.SingleJarImpl parentProvide, ZomboidJarProcessorManager jarProcessorManager, SingleJarEnvType env) {
			super(parentProvide, jarProcessorManager);
			this.env = env;
		}

		public static ProcessedNamedZomboidProvider.SingleJarImpl server(NamedZomboidProvider.SingleJarImpl parentProvide, ZomboidJarProcessorManager jarProcessorManager) {
			return new ProcessedNamedZomboidProvider.SingleJarImpl(parentProvide, jarProcessorManager, SingleJarEnvType.SERVER);
		}

		public static ProcessedNamedZomboidProvider.SingleJarImpl client(NamedZomboidProvider.SingleJarImpl parentProvide, ZomboidJarProcessorManager jarProcessorManager) {
			return new ProcessedNamedZomboidProvider.SingleJarImpl(parentProvide, jarProcessorManager, SingleJarEnvType.CLIENT);
		}

		@Override
		public ZomboidJar getEnvOnlyJar() {
			return getProcessedJar(getParentProvider().getEnvOnlyJar());
		}

		@Override
		public SingleJarEnvType env() {
			return env;
		}
	}

    public static final class CompleteJarImpl extends ProcessedNamedZomboidProvider<CompleteJarZomboidProvider, NamedZomboidProvider.CompleteJarImpl> implements CompleteJar {
        public CompleteJarImpl(NamedZomboidProvider.CompleteJarImpl parentProvide, ZomboidJarProcessorManager jarProcessorManager) {
            super(parentProvide, jarProcessorManager);
        }

        @Override
        public ZomboidJar getZomboidJar() {
            return getProcessedJar(getParentProvider().getZomboidJar());
        }
    }
}

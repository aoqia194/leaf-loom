/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021-2025 FabricMC
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

package dev.aoqia.leaf.loom.extension;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import javax.inject.Inject;

import dev.aoqia.leaf.loom.util.copygamefile.CopyGameFile;
import dev.aoqia.leaf.loom.util.copygamefile.CopyGameFileBuilder;

import org.gradle.api.Project;
import org.gradle.api.configuration.BuildFeatures;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

import dev.aoqia.leaf.loom.LoomGradleExtension;
import dev.aoqia.leaf.loom.api.mappings.intermediate.IntermediateMappingsProvider;
import dev.aoqia.leaf.loom.api.mappings.layered.MappingsNamespace;
import dev.aoqia.leaf.loom.configuration.InstallerData;
import dev.aoqia.leaf.loom.configuration.LoomDependencyManager;
import dev.aoqia.leaf.loom.configuration.accesswidener.AccessWidenerFile;
import dev.aoqia.leaf.loom.configuration.providers.mappings.IntermediaryMappingsProvider;
import dev.aoqia.leaf.loom.configuration.providers.mappings.LayeredMappingsFactory;
import dev.aoqia.leaf.loom.configuration.providers.mappings.MappingConfiguration;
import dev.aoqia.leaf.loom.configuration.providers.mappings.NoOpIntermediateMappingsProvider;
import dev.aoqia.leaf.loom.configuration.providers.zomboid.ZomboidMetadataProvider;
import dev.aoqia.leaf.loom.configuration.providers.zomboid.ZomboidProvider;
import dev.aoqia.leaf.loom.configuration.providers.zomboid.library.LibraryProcessorManager;
import dev.aoqia.leaf.loom.configuration.providers.zomboid.mapped.IntermediaryZomboidProvider;
import dev.aoqia.leaf.loom.configuration.providers.zomboid.mapped.NamedZomboidProvider;
import dev.aoqia.leaf.loom.util.download.Download;
import dev.aoqia.leaf.loom.util.download.DownloadBuilder;

public abstract class LoomGradleExtensionImpl extends LoomGradleExtensionApiImpl implements LoomGradleExtension {
	private final Project project;
	private final MixinExtension mixinApExtension;
	private final LoomFiles loomFiles;
	private final ConfigurableFileCollection unmappedMods;

	private final List<AccessWidenerFile> transitiveAccessWideners = new ArrayList<>();

	private LoomDependencyManager dependencyManager;
	private ZomboidMetadataProvider metadataProvider;
	private ZomboidProvider zomboidProvider;
	private MappingConfiguration mappingConfiguration;
	private NamedZomboidProvider<?> namedZomboidProvider;
	private IntermediaryZomboidProvider<?> intermediaryZomboidProvider;
	private InstallerData installerData;
	private boolean refreshDeps;
	private final ListProperty<LibraryProcessorManager.LibraryProcessorFactory> libraryProcessorFactories;
	private final boolean configurationCacheActive;
	private final boolean isolatedProjectsActive;
	private final boolean isCollectingDependencyVerificationMetadata;
	private final Property<Boolean> disableObfuscation;
	private final Property<Boolean> dontRemap;

	@Inject
	protected abstract BuildFeatures getBuildFeatures();

	@Inject
	public LoomGradleExtensionImpl(Project project, LoomFiles files) {
		super(project, files);
		this.project = project;
		// Initiate with newInstance to allow gradle to decorate our extension
		this.mixinApExtension = project.getObjects().newInstance(MixinExtensionImpl.class, project);
		this.loomFiles = files;
		this.unmappedMods = project.files();

		// Setup the default intermediate mappings provider.
		setIntermediateMappingsProvider(IntermediaryMappingsProvider.class, provider -> {
			provider.getIntermediaryUrl()
					.convention(getIntermediaryUrl())
					.finalizeValueOnRead();

			provider.getRefreshDeps().set(project.provider(() -> LoomGradleExtension.get(project).refreshDeps()));
		});

		refreshDeps = manualRefreshDeps();
		libraryProcessorFactories = project.getObjects().listProperty(LibraryProcessorManager.LibraryProcessorFactory.class);
		libraryProcessorFactories.addAll(LibraryProcessorManager.DEFAULT_LIBRARY_PROCESSORS);
		libraryProcessorFactories.finalizeValueOnRead();

		configurationCacheActive = getBuildFeatures().getConfigurationCache().getActive().get();
		isolatedProjectsActive = getBuildFeatures().getIsolatedProjects().getActive().get();
		isCollectingDependencyVerificationMetadata = !project.getGradle().getStartParameter().getWriteDependencyVerifications().isEmpty();
		disableObfuscation = project.getObjects().property(Boolean.class);
		dontRemap = project.getObjects().property(Boolean.class);

		disableObfuscation.set(project.provider(() -> GradleUtils.getBooleanProperty(getProject(), Constants.Properties.DISABLE_OBFUSCATION)));
		disableObfuscation.finalizeValueOnRead();

		dontRemap.set(disableObfuscation.map(notObfuscated -> notObfuscated || GradleUtils.getBooleanProperty(getProject(), Constants.Properties.DONT_REMAP)));
		dontRemap.finalizeValueOnRead();

		if (refreshDeps) {
			project.getLogger().lifecycle("Refresh dependencies is in use, loom will be significantly slower.");
		}

		if (isolatedProjectsActive) {
			project.getLogger().lifecycle("Isolated projects is enabled, Loom support is highly experimental, not all features will be enabled.");
		}
	}

	@Override
	protected Project getProject() {
		return project;
	}

	@Override
	public LoomFiles getFiles() {
		return loomFiles;
	}

	@Override
	public ZomboidMetadataProvider getMetadataProvider() {
		return Objects.requireNonNull(metadataProvider, "Cannot get ZomboidMetadataProvider before it has been setup");
	}

	@Override
	public void setMetadataProvider(ZomboidMetadataProvider metadataProvider) {
		this.metadataProvider = metadataProvider;
	}

	@Override
	public ZomboidProvider getZomboidProvider() {
		return Objects.requireNonNull(zomboidProvider, "Cannot get ZomboidProvider before it has been setup");
	}

	@Override
	public void setZomboidProvider(ZomboidProvider provider) {
		this.zomboidProvider = provider;
	}

	@Override
	public MappingConfiguration getMappingConfiguration() {
		if (disableObfuscation()) {
			throw new UnsupportedOperationException("Cannot get mappings configuration in a non-obfuscated environment");
		}

		return Objects.requireNonNull(mappingConfiguration, "Cannot get MappingsProvider before it has been setup");
	}

	@Override
	public void setMappingConfiguration(MappingConfiguration mappingConfiguration) {
		if (disableObfuscation()) {
			throw new UnsupportedOperationException("Cannot set mappings configuration in a non-obfuscated environment");
		}

		this.mappingConfiguration = mappingConfiguration;
	}

	@Override
	public NamedZomboidProvider<?> getNamedZomboidProvider() {
		return Objects.requireNonNull(namedZomboidProvider, "Cannot get NamedZomboidProvider before it has been setup");
	}

	@Override
	public IntermediaryZomboidProvider<?> getIntermediaryZomboidProvider() {
		return Objects.requireNonNull(intermediaryZomboidProvider, "Cannot get IntermediaryZomboidProvider before it has been setup");
	}

	@Override
	public void setNamedZomboidProvider(NamedZomboidProvider<?> provider) {
		this.namedZomboidProvider = provider;
	}

	@Override
	public void setIntermediaryZomboidProvider(IntermediaryZomboidProvider<?> provider) {
		this.intermediaryZomboidProvider = provider;
	}

	@Override
	public void noIntermediateMappings() {
		setIntermediateMappingsProvider(NoOpIntermediateMappingsProvider.class, p -> { });
	}

	@Override
	public FileCollection getZomboidJarsCollection(MappingsNamespace mappingsNamespace) {
		return getProject().files(
			getProject().provider(() ->
				getProject().files(getZomboidJars(mappingsNamespace).stream().map(Path::toFile).toList())
			)
		);
	}

	@Override
	public ConfigurableFileCollection getUnmappedModCollection() {
		return unmappedMods;
	}

	public void setInstallerData(InstallerData object) {
		this.installerData = object;
	}

	@Override
	public InstallerData getInstallerData() {
		return installerData;
	}

	@Override
	public MixinExtension getMixin() {
		return this.mixinApExtension;
	}

	@Override
	public List<AccessWidenerFile> getTransitiveAccessWideners() {
		return transitiveAccessWideners;
	}

	@Override
	public void addTransitiveAccessWideners(List<AccessWidenerFile> accessWidenerFiles) {
		transitiveAccessWideners.addAll(accessWidenerFiles);
	}

	@Override
	public DownloadBuilder download(String url) {
		DownloadBuilder builder;

		try {
			builder = Download.create(url);
		} catch (URISyntaxException e) {
			throw new RuntimeException("Failed to create downloader for: " + e);
		}

		if (project.getGradle().getStartParameter().isOffline()) {
			builder.offline();
		}

		if (manualRefreshDeps()) {
			builder.forceDownload();
		}

		return builder;
	}

    @Override
    public CopyGameFileBuilder copyGameFile(String url) {
        CopyGameFileBuilder builder = CopyGameFile.create(Path.of(url));

        if (manualRefreshDeps()) {
            builder.forced();
        }

        return builder;
    }

	private boolean manualRefreshDeps() {
		return project.getGradle().getStartParameter().isRefreshDependencies() || Boolean.getBoolean("loom.refresh");
	}

	@Override
	public boolean refreshDeps() {
		return refreshDeps;
	}

	@Override
	public void setRefreshDeps(boolean refreshDeps) {
		this.refreshDeps = refreshDeps;
	}

	@Override
	public ListProperty<LibraryProcessorManager.LibraryProcessorFactory> getLibraryProcessors() {
		return libraryProcessorFactories;
	}

	@Override
	public ListProperty<RemapperExtensionHolder> getRemapperExtensions() {
		return remapperExtensions;
	}

	@Override
	public Collection<LayeredMappingsFactory> getLayeredMappingFactories() {
		if (disableObfuscation()) {
			throw new UnsupportedOperationException("Cannot get layered mapping factories in a non-obfuscated environment");
		}

		hasEvaluatedLayeredMappings = true;
		return Collections.unmodifiableCollection(layeredMappingsDependencyMap.values());
	}

	@Override
	protected <T extends IntermediateMappingsProvider> void configureIntermediateMappingsProviderInternal(T provider) {
		provider.getMinecraftVersion().set(getProject().provider(() -> getZomboidProvider().zomboidVersion()));
		provider.getMinecraftVersion().disallowChanges();

		provider.getDownloader().set(this::download);
		provider.getDownloader().disallowChanges();

		provider.getUseSplitOfficialNamespaces().set(getProject().provider(() -> getZomboidProvider().isLegacySplitOfficialNamespaceVersion()));
		provider.getUseSplitOfficialNamespaces().disallowChanges();
	}

	@Override
	public boolean isConfigurationCacheActive() {
		return configurationCacheActive;
	}

	@Override
	public boolean isProjectIsolationActive() {
		return isolatedProjectsActive;
	}

	@Override
	public boolean isCollectingDependencyVerificationMetadata() {
		return isCollectingDependencyVerificationMetadata;
	}

	@Override
	public boolean dontRemapOutputs() {
		return dontRemap.get();
	}

	@Override
	public boolean disableObfuscation() {
		return disableObfuscation.get();
	}
}

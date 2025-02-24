/*
 * This file is part of leaf-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021-2024 aoqia, FabricMC
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

import javax.inject.Inject;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.*;

import dev.aoqia.leaf.loom.LoomGradleExtension;
import dev.aoqia.leaf.loom.api.mappings.layered.MappingsNamespace;
import dev.aoqia.leaf.loom.configuration.InstallerData;
import dev.aoqia.leaf.loom.configuration.LoomDependencyManager;
import dev.aoqia.leaf.loom.configuration.accesswidener.AccessWidenerFile;
import dev.aoqia.leaf.loom.configuration.providers.mappings.LayeredMappingsFactory;
import dev.aoqia.leaf.loom.configuration.providers.mappings.MappingConfiguration;
import dev.aoqia.leaf.loom.configuration.providers.zomboid.ZomboidMetadataProvider;
import dev.aoqia.leaf.loom.configuration.providers.zomboid.ZomboidProvider;
import dev.aoqia.leaf.loom.configuration.providers.zomboid.library.LibraryProcessorManager;
import dev.aoqia.leaf.loom.configuration.providers.zomboid.mapped.NamedZomboidProvider;
import dev.aoqia.leaf.loom.util.copygamefile.CopyGameFile;
import dev.aoqia.leaf.loom.util.copygamefile.CopyGameFileBuilder;
import dev.aoqia.leaf.loom.util.download.Download;
import dev.aoqia.leaf.loom.util.download.DownloadBuilder;
import org.gradle.api.Project;
import org.gradle.api.configuration.BuildFeatures;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.ListProperty;

public abstract class LoomGradleExtensionImpl extends LoomGradleExtensionApiImpl implements LoomGradleExtension {
    private final Project project;
    private final MixinExtension mixinApExtension;
    private final LoomFiles loomFiles;
    private final ConfigurableFileCollection unmappedMods;

    private final List<AccessWidenerFile> transitiveAccessWideners = new ArrayList<>();
    private final ListProperty<LibraryProcessorManager.LibraryProcessorFactory> libraryProcessorFactories;
    private final boolean configurationCacheActive;
    private final boolean isolatedProjectsActive;
    private LoomDependencyManager dependencyManager;
    private ZomboidMetadataProvider clientMetadataProvider;
    private ZomboidMetadataProvider serverMetadataProvider;
    private ZomboidProvider zomboidProvider;
    private MappingConfiguration mappingConfiguration;
    private NamedZomboidProvider<?> namedZomboidProvider;
    private InstallerData installerData;
    private boolean refreshDeps;

    @Inject
    public LoomGradleExtensionImpl(Project project, LoomFiles files) {
        super(project, files);
        this.project = project;
        // Initiate with newInstance to allow gradle to decorate our extension
        this.mixinApExtension = project.getObjects().newInstance(MixinExtensionImpl.class, project);
        this.loomFiles = files;
        this.unmappedMods = project.files();

        // Setup the default intermediate mappings provider.
        //        setIntermediateMappingsProvider(IntermediaryMappingsProvider.class, provider -> {
        //            provider.getIntermediaryUrl().convention(getIntermediaryUrl()).finalizeValueOnRead();
        //
        //            provider.getRefreshDeps()
        //                .set(project.provider(() -> LoomGradleExtension.get(project).refreshDeps()));
        //        });

        refreshDeps = manualRefreshDeps();
        libraryProcessorFactories =
            project.getObjects().listProperty(LibraryProcessorManager.LibraryProcessorFactory.class);
        libraryProcessorFactories.addAll(LibraryProcessorManager.DEFAULT_LIBRARY_PROCESSORS);
        libraryProcessorFactories.finalizeValueOnRead();

        configurationCacheActive =
            getBuildFeatures().getConfigurationCache().getActive().get();
        isolatedProjectsActive =
            getBuildFeatures().getIsolatedProjects().getActive().get();

        if (refreshDeps) {
            project.getLogger().lifecycle("Refresh dependencies is in use, loom will be significantly slower.");
        }

        if (isolatedProjectsActive) {
            project.getLogger().lifecycle("Isolated projects is enabled, Loom support is highly experimental, not all features will be enabled.");
        }
    }

    @Inject
    protected abstract BuildFeatures getBuildFeatures();

    private boolean manualRefreshDeps() {
        return project.getGradle().getStartParameter().isRefreshDependencies() || Boolean.getBoolean("loom.refresh");
    }

    @Override
    public ConfigurableFileCollection getUnmappedModCollection() {
        return unmappedMods;
    }

    @Override
    public InstallerData getInstallerData() {
        return installerData;
    }

    public void setInstallerData(InstallerData object) {
        this.installerData = object;
    }

    @Override
    public LoomDependencyManager getDependencyManager() {
        return Objects.requireNonNull(dependencyManager, "Cannot get LoomDependencyManager before it has been setup");
    }

    @Override
    public void setDependencyManager(LoomDependencyManager dependencyManager) {
        this.dependencyManager = dependencyManager;
    }

    @Override
    public ZomboidMetadataProvider getClientMetadataProvider() {
        return Objects.requireNonNull(
            clientMetadataProvider, "Cannot get client ZomboidMetadataProvider before it has been setup");
    }

    @Override
    public void setClientMetadataProvider(ZomboidMetadataProvider metadataProvider) {
        this.clientMetadataProvider = metadataProvider;
    }

    @Override
    public ZomboidMetadataProvider getServerMetadataProvider() {
        return Objects.requireNonNull(
            serverMetadataProvider, "Cannot get server ZomboidMetadataProvider before it has been setup");
    }

    @Override
    public void setServerMetadataProvider(ZomboidMetadataProvider metadataProvider) {
        this.serverMetadataProvider = metadataProvider;
    }

    @Override
    public MappingConfiguration getMappingConfiguration() {
        return Objects.requireNonNull(mappingConfiguration, "Cannot get MappingsProvider before it has been setup");
    }

    @Override
    public void setMappingConfiguration(MappingConfiguration mappingConfiguration) {
        this.mappingConfiguration = mappingConfiguration;
    }

    @Override
    public ZomboidProvider getZomboidProvider() {
        return Objects.requireNonNull(zomboidProvider, "Cannot get ZomboidProvider before it has been setup");
    }

    @Override
    public void setZomboidProvider(ZomboidProvider zomboidProvider) {
        this.zomboidProvider = zomboidProvider;
    }

    @Override
    public NamedZomboidProvider<?> getNamedZomboidProvider() {
        return Objects.requireNonNull(
            namedZomboidProvider, "Cannot get NamedMinecraftProvider before it has been setup");
    }

    @Override
    public void setNamedZomboidProvider(NamedZomboidProvider<?> namedZomboidProvider) {
        this.namedZomboidProvider = namedZomboidProvider;
    }

    @Override
    public FileCollection getZomboidJarsCollection(MappingsNamespace mappingsNamespace) {
        return getProject().files(getProject().provider(() -> getProject()
            .files(getZomboidJars(mappingsNamespace).stream()
                .map(Path::toFile)
                .toList())));
    }

    @Override
    public boolean isRootProject() {
        return project.getRootProject() == project;
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
    public CopyGameFileBuilder copyGameFile(String srcPath) {
        CopyGameFileBuilder builder = CopyGameFile.create(Path.of(srcPath));

        if (manualRefreshDeps()) {
            builder.forced();
        }

        return builder;
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
        hasEvaluatedLayeredMappings = true;
        return Collections.unmodifiableCollection(layeredMappingsDependencyMap.values());
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
    public MixinExtension getMixin() {
        return this.mixinApExtension;
    }

    @Override
    protected Project getProject() {
        return project;
    }

    @Override
    public LoomFiles getFiles() {
        return loomFiles;
    }
}

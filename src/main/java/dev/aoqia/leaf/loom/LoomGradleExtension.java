/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016-2022 FabricMC
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

package dev.aoqia.leaf.loom;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

import dev.aoqia.leaf.loom.util.copygamefile.CopyGameFileBuilder;

import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.ListProperty;
import org.jetbrains.annotations.ApiStatus;

import dev.aoqia.leaf.loom.api.LoomGradleExtensionAPI;
import dev.aoqia.leaf.loom.api.mappings.layered.MappingsNamespace;
import dev.aoqia.leaf.loom.configuration.InstallerData;
import dev.aoqia.leaf.loom.configuration.LoomDependencyManager;
import dev.aoqia.leaf.loom.configuration.accesswidener.AccessWidenerFile;
import dev.aoqia.leaf.loom.configuration.providers.mappings.LayeredMappingsFactory;
import dev.aoqia.leaf.loom.configuration.providers.mappings.MappingConfiguration;
import dev.aoqia.leaf.loom.configuration.providers.zomboid.ZomboidMetadataProvider;
import dev.aoqia.leaf.loom.configuration.providers.zomboid.ZomboidProvider;
import dev.aoqia.leaf.loom.configuration.providers.zomboid.library.LibraryProcessorManager;
import dev.aoqia.leaf.loom.configuration.providers.zomboid.mapped.IntermediaryZomboidProvider;
import dev.aoqia.leaf.loom.configuration.providers.zomboid.mapped.NamedZomboidProvider;
import dev.aoqia.leaf.loom.extension.LoomFiles;
import dev.aoqia.leaf.loom.extension.MixinExtension;
import dev.aoqia.leaf.loom.extension.RemapperExtensionHolder;
import dev.aoqia.leaf.loom.util.download.DownloadBuilder;

@ApiStatus.Internal
public interface LoomGradleExtension extends LoomGradleExtensionAPI {
	static LoomGradleExtension get(Project project) {
		return (LoomGradleExtension) project.getExtensions().getByName("loom");
	}

	LoomFiles getFiles();

	ConfigurableFileCollection getUnmappedModCollection();

	void setInstallerData(InstallerData data);

	InstallerData getInstallerData();

	void setDependencyManager(LoomDependencyManager dependencyManager);

	LoomDependencyManager getDependencyManager();

	ZomboidMetadataProvider getMetadataProvider();

	void setMetadataProvider(ZomboidMetadataProvider metadataProvider);

	ZomboidProvider getZomboidProvider();

	void setZomboidProvider(ZomboidProvider zomboidProvider);

	MappingConfiguration getMappingConfiguration();

	void setMappingConfiguration(MappingConfiguration mappingConfiguration);

	NamedZomboidProvider<?> getNamedZomboidProvider();

	IntermediaryZomboidProvider<?> getIntermediaryZomboidProvider();

	void setNamedZomboidProvider(NamedZomboidProvider<?> provider);

	void setIntermediaryZomboidProvider(IntermediaryZomboidProvider<?> provider);

	default List<Path> getZomboidJars(MappingsNamespace mappingsNamespace) {
		return switch (mappingsNamespace) {
		case NAMED -> getNamedZomboidProvider().getZomboidJarPaths();
		case INTERMEDIARY -> getIntermediaryZomboidProvider().getZomboidJarPaths();
		case OFFICIAL, CLIENT_OFFICIAL, SERVER_OFFICIAL -> getZomboidProvider().getZomboidJars();
		};
	}

	FileCollection getZomboidJarsCollection(MappingsNamespace mappingsNamespace);

	@Override
	MixinExtension getMixin();

	List<AccessWidenerFile> getTransitiveAccessWideners();

	void addTransitiveAccessWideners(List<AccessWidenerFile> accessWidenerFiles);

	DownloadBuilder download(String url);

	CopyGameFileBuilder copyGameFile(String path);

	boolean refreshDeps();

	void setRefreshDeps(boolean refreshDeps);

	ListProperty<LibraryProcessorManager.LibraryProcessorFactory> getLibraryProcessors();

	ListProperty<RemapperExtensionHolder> getRemapperExtensions();

	Collection<LayeredMappingsFactory> getLayeredMappingFactories();

	boolean isConfigurationCacheActive();

	boolean isProjectIsolationActive();

	/**
	 * @return true when '--write-verification-metadata` is set
	 */
	boolean isCollectingDependencyVerificationMetadata();
}

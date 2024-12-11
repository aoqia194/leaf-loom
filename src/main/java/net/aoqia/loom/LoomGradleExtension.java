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

package net.aoqia.loom;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

import net.aoqia.loom.api.LoomGradleExtensionAPI;
import net.aoqia.loom.api.mappings.layered.MappingsNamespace;
import net.aoqia.loom.configuration.InstallerData;
import net.aoqia.loom.configuration.LoomDependencyManager;
import net.aoqia.loom.configuration.accesswidener.AccessWidenerFile;
import net.aoqia.loom.configuration.providers.mappings.LayeredMappingsFactory;
import net.aoqia.loom.configuration.providers.mappings.MappingConfiguration;
import net.aoqia.loom.configuration.providers.zomboid.ZomboidMetadataProvider;
import net.aoqia.loom.configuration.providers.zomboid.ZomboidProvider;
import net.aoqia.loom.configuration.providers.zomboid.library.LibraryProcessorManager;
import net.aoqia.loom.configuration.providers.zomboid.mapped.NamedZomboidProvider;
import net.aoqia.loom.extension.LoomFiles;
import net.aoqia.loom.extension.MixinExtension;
import net.aoqia.loom.extension.RemapperExtensionHolder;
import net.aoqia.loom.util.copygamefile.CopyGameFileBuilder;
import net.aoqia.loom.util.download.DownloadBuilder;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.ListProperty;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public interface LoomGradleExtension extends LoomGradleExtensionAPI {
    static LoomGradleExtension get(Project project) {
        return (LoomGradleExtension) project.getExtensions().getByName("loom");
    }

    LoomFiles getFiles();

    ConfigurableFileCollection getUnmappedModCollection();

    InstallerData getInstallerData();

    void setInstallerData(InstallerData data);

    LoomDependencyManager getDependencyManager();

    void setDependencyManager(LoomDependencyManager dependencyManager);

    ZomboidMetadataProvider getClientMetadataProvider();

    void setClientMetadataProvider(ZomboidMetadataProvider metadataProvider);

    ZomboidMetadataProvider getServerMetadataProvider();

    void setServerMetadataProvider(ZomboidMetadataProvider metadataProvider);

    MappingConfiguration getMappingConfiguration();

    void setMappingConfiguration(MappingConfiguration mappingConfiguration);

    default List<Path> getZomboidJars(MappingsNamespace mappingsNamespace) {
        return switch (mappingsNamespace) {
            case NAMED -> getNamedZomboidProvider().getZomboidJarPaths();
            case OFFICIAL -> getZomboidProvider().getZomboidJars();
        };
    }

    ZomboidProvider getZomboidProvider();

    void setZomboidProvider(ZomboidProvider zomboidProvider);

    NamedZomboidProvider<?> getNamedZomboidProvider();

    void setNamedZomboidProvider(NamedZomboidProvider<?> namedZomboidProvider);

    FileCollection getZomboidJarsCollection(MappingsNamespace mappingsNamespace);

    boolean isRootProject();

    @Override
    MixinExtension getMixin();

    List<AccessWidenerFile> getTransitiveAccessWideners();

    void addTransitiveAccessWideners(List<AccessWidenerFile> accessWidenerFiles);

    CopyGameFileBuilder copyGameFile(String srcPath);

    DownloadBuilder download(String url);

    boolean refreshDeps();

    void setRefreshDeps(boolean refreshDeps);

    ListProperty<LibraryProcessorManager.LibraryProcessorFactory> getLibraryProcessors();

    ListProperty<RemapperExtensionHolder> getRemapperExtensions();

    Collection<LayeredMappingsFactory> getLayeredMappingFactories();

    boolean isConfigurationCacheActive();

	boolean isProjectIsolationActive();
}

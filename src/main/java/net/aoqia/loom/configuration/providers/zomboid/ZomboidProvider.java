/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2018-2021 FabricMC
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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import com.google.common.base.Preconditions;
import net.aoqia.loom.LoomGradleExtension;
import net.aoqia.loom.LoomGradlePlugin;
import net.aoqia.loom.api.mappings.layered.MappingsNamespace;
import net.aoqia.loom.configuration.ConfigContext;
import net.aoqia.loom.configuration.providers.zomboid.assets.AssetIndex;
import net.aoqia.loom.util.Constants;
import net.aoqia.loom.util.FileSystemUtil;
import net.aoqia.loom.util.JarUtil;
import net.aoqia.loom.util.MirrorUtil;
import net.aoqia.loom.util.copygamefile.CopyGameFileExecutor;
import net.aoqia.loom.util.copygamefile.GradleCopyGameFileProgressListener;
import net.aoqia.loom.util.gradle.GradleUtils;
import net.aoqia.loom.util.gradle.ProgressGroup;
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ZomboidProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(ZomboidProvider.class);

    private final ZomboidMetadataProvider clientMetadataProvider;
    private final ZomboidMetadataProvider serverMetadataProvider;
    private final ConfigContext configContext;
    private File zomboidClientJar;
    private File zomboidServerJar;

    public ZomboidProvider(ZomboidMetadataProvider clientMetadataProvider,
        ZomboidMetadataProvider serverMetadataProvider,
        ConfigContext configContext) {
        this.clientMetadataProvider = clientMetadataProvider;
        this.serverMetadataProvider = serverMetadataProvider;
        this.configContext = configContext;
    }

    public ZomboidProvider(ZomboidMetadataProvider clientMetadataProvider,
        ConfigContext configContext) {
        this.clientMetadataProvider = clientMetadataProvider;
        this.configContext = configContext;
        serverMetadataProvider = null;
    }

    public void provide() throws Exception {
        initFiles();

        final int copyFileThreads = Math.min(Runtime.getRuntime().availableProcessors(), 10);
        final Project project = getProject();

        // Copy client files
        if (provideClient()) {
            final ZomboidVersionMeta.JavaVersion javaVersion = getClientVersionInfo().java_version();
            if (javaVersion != null) {
                final int requiredMajorJavaVersion = getClientVersionInfo().java_version().major_version();
                final JavaVersion requiredJavaVersion = JavaVersion.toVersion(requiredMajorJavaVersion);

                if (!JavaVersion.current().isCompatibleWith(requiredJavaVersion)) {
                    throw new IllegalStateException(
                        "Zomboid " + clientZomboidVersion() + " requires Java " + requiredJavaVersion
                        + " but Gradle is using " + JavaVersion.current());
                }
            }

            final String gameInstallPath = MirrorUtil.getGameInstallPath(project);
            final AssetIndex assetIndex = getClientAssetIndex();

            try (final FileSystemUtil.Delegate outputJar = FileSystemUtil.getJarFileSystem(zomboidClientJar, true)) {
                try (ProgressGroup progressGroup = new ProgressGroup(project, "Copy Client Game Assets");
                     CopyGameFileExecutor executor = new CopyGameFileExecutor(copyFileThreads)) {
                    for (AssetIndex.Object object : assetIndex.getObjects()) {
                        final String path = object.path();

                        // Exclude certain folders.
                        if (path.contains("jre")
                            || path.contains("jre64")
                            || path.endsWith(".bat")
                            || path.endsWith(".exe")) {
                            continue;
                        }

                        final String sha1 = object.hash();
                        final Path srcPath = Path.of(gameInstallPath, path);
                        final Path dstPath = outputJar.get().getPath(getGameFilePath(object).toString());
                        // System.out.println("copyGameAssets srcPath=(" + srcPath + "), dstPath=(" + dstPath + ")");

                        // File exists check.
                        if (!srcPath.toFile().exists()) {
                            if (GradleUtils.getBooleanProperty(project, Constants.Properties.IGNORE_MISSING_FILES)) {
                                continue;
                            }

                            throw new FileNotFoundException("Client game file '%s' does not exist.".formatted(srcPath));
                        }

                        getExtension().copyGameFile(srcPath.toString())
                            .sha1(sha1)
                            .progress(new GradleCopyGameFileProgressListener(object.path(),
                                progressGroup::createProgressLogger))
                            .copyGameFileFromPathAsync(dstPath, executor);
                    }
                }

                JarUtil.createManifest(outputJar);
            }
        }

        // Copy server files
        if (provideServer()) {
            final ZomboidVersionMeta.JavaVersion javaVersion = getServerVersionInfo().java_version();
            if (javaVersion != null) {
                final int requiredMajorJavaVersion = getServerVersionInfo().java_version().major_version();
                final JavaVersion requiredJavaVersion = JavaVersion.toVersion(requiredMajorJavaVersion);

                if (!JavaVersion.current().isCompatibleWith(requiredJavaVersion)) {
                    throw new IllegalStateException(
                        "Zomboid Server " + serverZomboidVersion() + " requires Java " + requiredJavaVersion
                        + " but Gradle is using " + JavaVersion.current());
                }
            }

            final String gameInstallPath = MirrorUtil.getGameInstallPath(project);
            final AssetIndex assetIndex = getServerAssetIndex();

            try (final FileSystemUtil.Delegate outputJar = FileSystemUtil.getJarFileSystem(zomboidServerJar, true)) {
                try (ProgressGroup progressGroup = new ProgressGroup(project, "Copy Server Game Assets");
                     CopyGameFileExecutor executor = new CopyGameFileExecutor(copyFileThreads)) {
                    for (AssetIndex.Object object : assetIndex.getObjects()) {
                        final String path = object.path();

                        // Exclude certain folders.
                        if (path.contains("jre")
                            || path.contains("jre64")
                            || path.endsWith(".bat")
                            || path.endsWith(".exe")) {
                            continue;
                        }

                        final String sha1 = object.hash();
                        final Path srcPath = Path.of(gameInstallPath, path);
                        final Path dstPath = outputJar.get().getPath(getGameFilePath(object).toString());
                        // System.out.println("copyGameAssets srcPath=(" + srcPath + "), dstPath=(" + dstPath + ")");

                        // File exists check.
                        if (!srcPath.toFile().exists()) {
                            if (GradleUtils.getBooleanProperty(project, Constants.Properties.IGNORE_MISSING_FILES)) {
                                continue;
                            }

                            throw new FileNotFoundException("Server game file '%s' does not exist.".formatted(srcPath));
                        }

                        getExtension().copyGameFile(srcPath.toString())
                            .sha1(sha1)
                            .progress(new GradleCopyGameFileProgressListener(object.path(),
                                progressGroup::createProgressLogger))
                            .copyGameFileFromPathAsync(dstPath, executor);
                    }
                }

                JarUtil.createManifest(outputJar);
            }
        }

        final ZomboidLibraryProvider libraryProvider = new ZomboidLibraryProvider(this, configContext.project());
        libraryProvider.provide();
    }

    private Path getGameFilePath(AssetIndex.Object object) {
        return Path.of(object.path().replace("\\", File.separator));
    }

    private AssetIndex getClientAssetIndex() throws IOException {
        final ZomboidVersionMeta.AssetIndex assetIndex =
            LoomGradlePlugin.GSON.fromJson(LoomGradlePlugin.GSON.toJson(getClientVersionInfo().asset_index()),
                ZomboidVersionMeta.AssetIndex.class);
        final File indexFile = new File(workingDir(), "zomboid_client_index_manifest.json");

        final String json = getExtension()
            .download(Constants.INDEX_MANIFEST_PATH + "client/" + MirrorUtil.getOsStringForUrl() + "/" +
                      clientZomboidVersion() + ".json")
            .sha1(assetIndex.sha1())
            .downloadString(indexFile.toPath());

        return LoomGradlePlugin.GSON.fromJson(json, AssetIndex.class);
    }

    private AssetIndex getServerAssetIndex() throws IOException {
        final ZomboidVersionMeta.AssetIndex assetIndex =
            LoomGradlePlugin.GSON.fromJson(LoomGradlePlugin.GSON.toJson(getServerVersionInfo().asset_index()),
                ZomboidVersionMeta.AssetIndex.class);
        final File indexFile = new File(workingDir(), "zomboid_server_index_manifest.json");

        final String json = getExtension()
            .download(Constants.INDEX_MANIFEST_PATH + "server/" + MirrorUtil.getOsStringForUrl() + "/" +
                      serverZomboidVersion() + ".json")
            .sha1(assetIndex.sha1())
            .downloadString(indexFile.toPath());

        return LoomGradlePlugin.GSON.fromJson(json, AssetIndex.class);
    }

    protected void initFiles() {
        if (provideClient()) {
            zomboidClientJar = file("zomboid-client.jar");
        }

        if (provideServer()) {
            zomboidServerJar = file("zomboid-server.jar");
        }
    }

    public ZomboidVersionMeta getClientVersionInfo() {
        return Objects.requireNonNull(clientMetadataProvider, "Metadata provider not setup")
            .getVersionMeta();
    }

    protected Project getProject() {
        return configContext.project();
    }

    public File getZomboidClientJar() {
        Preconditions.checkArgument(provideClient(), "Not configured to provide client jar");
        return zomboidClientJar;
    }

    protected boolean provideClient() {
        return true;
    }

    public String serverZomboidVersion() {
        return Objects.requireNonNull(serverMetadataProvider, "Metadata provider not setup")
            .getZomboidVersion();
    }

    public ZomboidVersionMeta getServerVersionInfo() {
        return Objects.requireNonNull(serverMetadataProvider, "Metadata provider not setup")
            .getVersionMeta();
    }

    public File dir(String path) {
        File dir = file(path);
        dir.mkdirs();
        return dir;
    }

    public File file(String path) {
        return new File(workingDir(), path);
    }

    public File workingDir() {
        return zomboidWorkingDirectory(configContext.project(), clientZomboidVersion());
    }

    public static File zomboidWorkingDirectory(Project project, String version) {
        LoomGradleExtension extension = LoomGradleExtension.get(project);
        File workingDir = new File(extension.getFiles().getUserCache(), version);
        workingDir.mkdirs();
        return workingDir;
    }

    public String clientZomboidVersion() {
        return Objects.requireNonNull(clientMetadataProvider, "Metadata provider not setup")
            .getZomboidVersion();
    }

    public Path path(String path) {
        return file(path).toPath();
    }

    // This may be the server bundler jar on newer versions prob not what you want.
    public File getZomboidServerJar() {
        Preconditions.checkArgument(provideServer(), "Not configured to provide server jar");
        return zomboidServerJar;
    }

    protected boolean provideServer() {
        return true;
    }

    public abstract List<Path> getZomboidJars();

    public abstract MappingsNamespace getOfficialNamespace();

    public boolean refreshDeps() {
        return getExtension().refreshDeps();
    }

    protected LoomGradleExtension getExtension() {
        return configContext.extension();
    }
}

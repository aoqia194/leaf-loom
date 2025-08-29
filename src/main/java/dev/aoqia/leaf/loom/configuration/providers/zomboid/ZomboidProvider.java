/*
 * This file is part of leaf-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2018-2021 aoqia, FabricMC
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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import dev.aoqia.leaf.loom.LoomGradleExtension;
import dev.aoqia.leaf.loom.LoomGradlePlugin;
import dev.aoqia.leaf.loom.api.mappings.layered.MappingsNamespace;
import dev.aoqia.leaf.loom.configuration.ConfigContext;
import dev.aoqia.leaf.loom.configuration.providers.zomboid.assets.AssetIndex;
import dev.aoqia.leaf.loom.util.*;
import dev.aoqia.leaf.loom.util.copygamefile.CopyGameFileExecutor;
import dev.aoqia.leaf.loom.util.copygamefile.GradleCopyGameFileProgressListener;
import dev.aoqia.leaf.loom.util.download.DownloadException;
import dev.aoqia.leaf.loom.util.gradle.GradleUtils;
import dev.aoqia.leaf.loom.util.gradle.ProgressGroup;

import com.google.common.base.Preconditions;
import org.apache.commons.io.FilenameUtils;
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

    public static File zomboidWorkingDirectory(Project project, String version) {
        LoomGradleExtension extension = LoomGradleExtension.get(project);
        File workingDir = new File(extension.getFiles().getUserCache(), version);
        workingDir.mkdirs();
        return workingDir;
    }

    public void provide() throws Exception {
        final Project project = getProject();
        final int copyFileThreads = Math.min(Runtime.getRuntime().availableProcessors(),
            10);

        initFiles();

        // Copy client files
        if (provideClient()) {
            packageGameFiles(project, copyFileThreads, false);
        }

        // Copy server files
        if (provideServer()) {
            packageGameFiles(project, copyFileThreads, true);
        }

        final ZomboidLibraryProvider libraryProvider = new ZomboidLibraryProvider(this,
            configContext.project());
        libraryProvider.provide();
    }

    private void packageGameFiles(Project project, int copyFileThreads,
        boolean isServer) throws IOException {
        final Platform.OperatingSystem os = Platform.CURRENT.getOperatingSystem();
        final Platform.Architecture arch = Platform.CURRENT.getArchitecture();
        final String includedArchStr = arch.is64Bit() ? "64/" : "32/";
        final String excludedArchStr = arch.is64Bit() ? "32/" : "64/";
        final String osLibsStr = os.isWindows() ? "win" : "linux";
        final String envStr = !isServer ? "Client" : "Server";

        final String version = !isServer ? clientZomboidVersion() : serverZomboidVersion();
        final ZomboidVersionMeta versionInfo = !isServer ? getClientVersionInfo() : getServerVersionInfo();

        final JavaVersion requiredJavaVersion = JavaVersion.toVersion(versionInfo.javaVersion());
        if (!JavaVersion.current().isCompatibleWith(requiredJavaVersion)) {
            throw new IllegalStateException(
                "Zomboid " + version + " requires Java " + requiredJavaVersion +
                " but Gradle is using " + JavaVersion.current());
        }

        final Path gameInstallPath = !isServer ? MirrorUtil.getClientGamePath(project)
            : MirrorUtil.getServerGamePath(project);

        final ArrayList<Path> extractedLibs = new ArrayList<>();
        final File jar = !isServer ? zomboidClientJar : zomboidServerJar;
        try (final FileSystemUtil.Delegate outputJar = FileSystemUtil.getJarFileSystem(jar, true);
             ProgressGroup progressGroup = new ProgressGroup(project,
                 "Copy %s Game Assets".formatted(envStr));
             CopyGameFileExecutor executor = new CopyGameFileExecutor(copyFileThreads)) {

            final AssetIndex assetIndex = !isServer ? getClientAssetIndex() : getServerAssetIndex();
            for (AssetIndex.Object object : assetIndex.getObjects()) {
                Path path = Path.of(FilenameUtils.separatorsToSystem(object.path()));
                Path flatPath = path;
                // Strip the stupid subfolder away if there is one
                if ((os.isLinux() && flatPath.startsWith("projectzomboid/")) ||
                    isServer && flatPath.startsWith("java/")) {
                    flatPath = flatPath.subpath(1, flatPath.getNameCount());
                }
                // A string specifically for literal checking like with `contains` below.
                String safePath = FilenameUtils.separatorsToUnix(flatPath.toString());

                // Exclude certain folders for dev environment.
                if (!safePath.endsWith(".class")) {
                    if (safePath.startsWith("ProjectZomboid64")
                        || safePath.startsWith("ProjectZomboid32")
                        || safePath.startsWith("projectzomboid")
                        || safePath.startsWith("terms and conditions.txt")
                        || safePath.startsWith("SVNRevision.txt")
                        || safePath.startsWith("jre/")
                        || safePath.startsWith("jre64/")
                        || safePath.startsWith("mods/")
                        || safePath.startsWith("logs/")
                        || safePath.startsWith("depotcache/")
                        || safePath.startsWith("steamapps/")
                        || safePath.startsWith("userdata/")
                        || safePath.startsWith("config/")
                        || safePath.startsWith("appcache/")
                        || safePath.startsWith("launcher/")
                        || safePath.startsWith("license/")
                        || safePath.startsWith("Workshop/")
                        || safePath.startsWith("steamapps/")
                        || safePath.startsWith(osLibsStr + excludedArchStr)
                        || safePath.endsWith(".json")
                        || safePath.endsWith(".bat")
                        || safePath.endsWith(".exe")
                        || safePath.endsWith(".sh")
                        || safePath.endsWith(".desktop")) {
                        continue;
                    }

                    if (isServer) {
                        if (safePath.startsWith("natives/" + osLibsStr + excludedArchStr)) {
                            continue;
                        }
                    }
                }

                final String hash = object.hash();
                // Because the src path still uses the unflattened path.
                final Path srcPath = gameInstallPath.resolve(path);
                Path dstPath = outputJar.get().getPath(flatPath.toString());

                // File exists check. We already check existing files in CopyGameFile.
                if (!srcPath.toFile().exists()) {
                    if (GradleUtils.getBooleanProperty(project,
                        Constants.Properties.IGNORE_MISSING_FILES)) {
                        continue;
                    }

                    throw new FileNotFoundException(
                        "%s game file '%s' does not exist.".formatted(envStr, srcPath));
                }

                // Send non class files (like media/ folder) to the extracted folder.
                // Speeds up processing time a LOT.
                // .LBC compiled LuaByteCode file is with .class because otherwise
                // KahLua will freak out. EDIT: This seems to not be the case anymore?
                if (!safePath.endsWith(".class")/* && !path.endsWith(".lbc")*/) {
                    // Add jar files to the classpath.
                    if (safePath.endsWith(".jar")) {
                        dstPath = extractedDir().toPath().resolve(flatPath.getFileName());
                        extractedLibs.add(dstPath);
                    } else if ((!isServer && safePath.contains(osLibsStr + includedArchStr)) ||
                               (isServer && safePath.contains("natives/")) ||
                               safePath.contains("java/")) {
                        // Flatten the path so that it gets extracted to root folder.
                        dstPath = extractedDir().toPath().resolve(flatPath.getFileName());
                    } else {
                        dstPath = extractedDir().toPath().resolve(flatPath);
                    }
                }

                final boolean fallback = GradleUtils.getBooleanProperty(project, Constants.Properties.FORCE_ATTRIBUTE_FALLBACK);
                getExtension().copyGameFile(srcPath.toString())
                    .fallback(fallback)
                    .sha1(hash)
                    .progress(new GradleCopyGameFileProgressListener(object.path(),
                        progressGroup::createProgressLogger))
                    .copyGameFileFromPathAsync(dstPath, executor);
            }

            JarUtil.createManifest(outputJar);
        }

        // Add the extracted libs to compile libraries (which also adds to runtime).
        final String config =
            !isServer ? Constants.Configurations.ZOMBOID_CLIENT_COMPILE_LIBRARIES
                : Constants.Configurations.ZOMBOID_SERVER_COMPILE_LIBRARIES;
        project.getDependencies()
            .add(config, project.files(extractedLibs, extractedDir().toPath()));
    }

    private AssetIndex getClientAssetIndex() throws IOException {
        final ZomboidVersionMeta.AssetIndex assetIndex = getClientVersionInfo().assetIndex();
        final File indexFile = new File(workingDir(), "zomboid_client_index_manifest.json");

        final String json = getExtension()
            .download(assetIndex.url())
            .sha1(assetIndex.sha1())
            .downloadString(indexFile.toPath());
        return LoomGradlePlugin.GSON.fromJson(json, AssetIndex.class);
    }

    private AssetIndex getServerAssetIndex() throws DownloadException {
        final ZomboidVersionMeta.AssetIndex assetIndex = getServerVersionInfo().assetIndex();
        final File serverIndexFile = new File(workingDir(), "zomboid_server_index_manifest.json");
        final File commonIndexFile = new File(workingDir(),
            "zomboid_server-common_index_manifest.json");

        final String serverJson = getExtension()
            .download(assetIndex.url())
            .sha1(assetIndex.sha1())
            .downloadString(serverIndexFile.toPath());
        final var serverIndexes = LoomGradlePlugin.GSON.fromJson(serverJson, AssetIndex.class);

        final String commonJson = getExtension()
            .download(assetIndex.url().replace(MirrorUtil.getOsStringForUrl(), "common"))
            // .sha1(assetIndex.sha1())
            .downloadString(commonIndexFile.toPath());
        final var commonIndexes = LoomGradlePlugin.GSON.fromJson(commonJson, AssetIndex.class);

        // Merging server-common indexes into server indexes index class and returning that.
        serverIndexes.objects().putAll(commonIndexes.objects());
        return serverIndexes;
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
        return Objects.requireNonNull(clientMetadataProvider,
                "Metadata provider not setup")
            .getVersionMeta();
    }

    protected Project getProject() {
        return configContext.project();
    }

    public File getZomboidClientJar() {
        Preconditions.checkArgument(provideClient(),
            "Not configured to provide client jar");
        return zomboidClientJar;
    }

    protected boolean provideClient() {
        return true;
    }

    public String serverZomboidVersion() {
        return Objects.requireNonNull(serverMetadataProvider,
                "Metadata provider not setup")
            .getZomboidVersion();
    }

    public ZomboidVersionMeta getServerVersionInfo() {
        return Objects.requireNonNull(serverMetadataProvider,
                "Metadata provider not setup")
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

    public File extractedDir() {
        return this.workingDir().toPath().resolve("extracted").toFile();
    }

    public String clientZomboidVersion() {
        return Objects.requireNonNull(clientMetadataProvider,
                "Metadata provider not setup")
            .getZomboidVersion();
    }

    public Path path(String path) {
        return file(path).toPath();
    }

    public File getZomboidServerJar() {
        Preconditions.checkArgument(provideServer(),
            "Not configured to provide server jar");
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
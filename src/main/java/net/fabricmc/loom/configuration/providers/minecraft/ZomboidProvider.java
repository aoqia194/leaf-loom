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

package net.fabricmc.loom.configuration.providers.minecraft;

import com.google.common.base.Preconditions;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.loom.configuration.ConfigContext;
import net.fabricmc.loom.configuration.providers.BundleMetadata;
import net.fabricmc.loom.util.copygamefile.CopyGameFileExecutor;
import net.fabricmc.loom.util.copygamefile.GradleCopyGameFileProgressListener;
import net.fabricmc.loom.util.gradle.ProgressGroup;
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ZomboidProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(ZomboidProvider.class);

    private final ZomboidMetadataProvider metadataProvider;
    private final ConfigContext configContext;
    private File zomboidClientJar;
    private File zomboidServerJar;

    @Nullable
    private BundleMetadata serverBundleMetadata;

    public ZomboidProvider(ZomboidMetadataProvider metadataProvider, ConfigContext configContext) {
        this.metadataProvider = metadataProvider;
        this.configContext = configContext;
    }

    public void provide() throws Exception {
        initFiles();

        final ZomboidVersionMeta.JavaVersion javaVersion = getVersionInfo().javaVersion();

        if (javaVersion != null) {
            final int requiredMajorJavaVersion = getVersionInfo().javaVersion().majorVersion();
            final JavaVersion requiredJavaVersion = JavaVersion.toVersion(requiredMajorJavaVersion);

            if (!JavaVersion.current().isCompatibleWith(requiredJavaVersion)) {
                throw new IllegalStateException("Zomboid " + zomboidVersion() + " requires Java " + requiredJavaVersion
                        + " but Gradle is using " + JavaVersion.current());
            }
        }

        downloadJars();

        if (provideServer()) {
            serverBundleMetadata = BundleMetadata.fromJar(zomboidServerJar.toPath());
        }

        final MinecraftLibraryProvider libraryProvider = new MinecraftLibraryProvider(this, configContext.project());
        libraryProvider.provide();
    }

    protected void initFiles() {
        if (provideClient()) {
            zomboidClientJar = file("zomboid-client.jar");
        }

        if (provideServer()) {
            zomboidServerJar = file("zomboid-server.jar");
        }
    }

    protected boolean provideClient() {
        return true;
    }

    protected boolean provideServer() {
        return true;
    }

    public File file(String path) {
        return new File(workingDir(), path);
    }

    public File workingDir() {
        return zomboidWorkingDirectory(configContext.project(), zomboidVersion());
    }

    public String zomboidVersion() {
        return Objects.requireNonNull(metadataProvider, "Metadata provider not setup")
                .getZomboidVersion();
    }

    public static File zomboidWorkingDirectory(Project project, String version) {
        LoomGradleExtension extension = LoomGradleExtension.get(project);
        File workingDir = new File(extension.getFiles().getUserCache(), version);
        workingDir.mkdirs();
        return workingDir;
    }

    private void downloadJars() throws IOException {
        try (ProgressGroup progressGroup = new ProgressGroup(getProject(), "Download Minecraft jars");
                CopyGameFileExecutor executor = new CopyGameFileExecutor(2)) {
            if (provideClient()) {
                final ZomboidVersionMeta.Download client = getVersionInfo().download("client");
                getExtension()
                        .copyGameFile(client.url())
                        .sha1(client.sha1())
                        .progress(new GradleCopyGameFileProgressListener(
                                "Minecraft client", progressGroup::createProgressLogger))
                        .copyGameFileFromPathAsync(zomboidClientJar.toPath(), executor);
            }

            if (provideServer()) {
                final ZomboidVersionMeta.Download server = getVersionInfo().download("server");
                getExtension()
                        .copyGameFile(server.url())
                        .sha1(server.sha1())
                        .progress(new GradleCopyGameFileProgressListener(
                                "Minecraft server", progressGroup::createProgressLogger))
                        .copyGameFileFromPathAsync(zomboidServerJar.toPath(), executor);
            }
        }
    }

    protected Project getProject() {
        return configContext.project();
    }

    protected LoomGradleExtension getExtension() {
        return configContext.extension();
    }

    public ZomboidVersionMeta getVersionInfo() {
        return Objects.requireNonNull(metadataProvider, "Metadata provider not setup")
                .getVersionMeta();
    }

    public File dir(String path) {
        File dir = file(path);
        dir.mkdirs();
        return dir;
    }

    public Path path(String path) {
        return file(path).toPath();
    }

    public File getZomboidClientJar() {
        Preconditions.checkArgument(provideClient(), "Not configured to provide client jar");
        return zomboidClientJar;
    }

    // This may be the server bundler jar on newer versions prob not what you want.
    public File getZomboidServerJar() {
        Preconditions.checkArgument(provideServer(), "Not configured to provide server jar");
        return zomboidServerJar;
    }

    @Nullable
    public BundleMetadata getServerBundleMetadata() {
        return serverBundleMetadata;
    }

    public abstract List<Path> getZomboidJars();

    public abstract MappingsNamespace getOfficialNamespace();

    public boolean refreshDeps() {
        return getExtension().refreshDeps();
    }
}

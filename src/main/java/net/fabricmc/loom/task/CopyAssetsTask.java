/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016-2021 FabricMC
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

package net.fabricmc.loom.task;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.configuration.ide.RunConfigSettings;
import net.fabricmc.loom.configuration.providers.minecraft.ZomboidVersionMeta;
import net.fabricmc.loom.configuration.providers.minecraft.assets.AssetIndex;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.MirrorUtil;
import net.fabricmc.loom.util.copygamefile.CopyGameFileExecutor;
import net.fabricmc.loom.util.copygamefile.CopyGameFileFactory;
import net.fabricmc.loom.util.copygamefile.GradleCopyGameFileProgressListener;
import net.fabricmc.loom.util.download.DownloadFactory;
import net.fabricmc.loom.util.gradle.ProgressGroup;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;

public abstract class CopyAssetsTask extends AbstractLoomTask {
    @Inject
    public CopyAssetsTask() {
        final ZomboidVersionMeta versionInfo =
            getExtension().getZomboidProvider().getVersionInfo();
        final File assetsDir = new File(getExtension().getFiles().getUserCache(), "assets");

        getAssetsDirectory().set(assetsDir);
        getCopyFileThreads().convention(Math.min(Runtime.getRuntime().availableProcessors(), 10));
        getZomboidVersion().set(versionInfo.id());
        getZomboidVersion().finalizeValue();

        RunConfigSettings client = getExtension().getRunConfigs().findByName("client");
        String runDir = client != null ? client.getRunDir() : "run";
        getLegacyResourcesDirectory().set(new File(getProject().getProjectDir(), runDir + "/resources"));

        getResourcesBase().set(MirrorUtil.getResourcesBase(getProject()));
        getResourcesBase().finalizeValue();

        getServerResourcesBase().set(MirrorUtil.getServerResourcesBase(getProject()));
        getServerResourcesBase().finalizeValue();

        getAssetsIndexJson().set(LoomGradlePlugin.GSON.toJson(
            getExtension().getZomboidProvider().getVersionInfo().assetIndex()));

        getAssetsDirectory().finalizeValueOnRead();
        getLegacyResourcesDirectory().finalizeValueOnRead();
    }

    @Input
    public abstract Property<Integer> getCopyFileThreads();

    @Input
    public abstract Property<String> getZomboidVersion();

    @Input
    public abstract Property<String> getResourcesBase();

    @Input
    public abstract Property<String> getServerResourcesBase();

    @Input
    protected abstract Property<String> getAssetsIndexJson();

    @OutputDirectory
    public abstract RegularFileProperty getAssetsDirectory();

    @OutputDirectory
    public abstract RegularFileProperty getLegacyResourcesDirectory();

    @TaskAction
    public void copyGameAssets() throws IOException {
        final AssetIndex assetIndex = getAssetIndex();

        try (ProgressGroup progressGroup = new ProgressGroup("Copy Game Assets", getProgressLoggerFactory());
             CopyGameFileExecutor executor =
                 new CopyGameFileExecutor(getCopyFileThreads().get())) {
            // For each asset object in the AssetIndex file
            for (AssetIndex.Object object : assetIndex.getObjects()) {
                final String sha1 = object.hash();
                final String path = getResourcesBase().get() + File.separator;

                getCopyGameFileFactory()
                    .copyGameFile(path)
                    .sha1(sha1)
                    .progress(new GradleCopyGameFileProgressListener(object.path(),
                        progressGroup::createProgressLogger))
                    .copyGameFileFromPathAsync(getAssetsPath(object), executor);
            }
        }
    }

    @Inject
    protected abstract ProgressLoggerFactory getProgressLoggerFactory();

    private AssetIndex getAssetIndex() throws IOException {
        final String versionFile = getZomboidVersion().get() + ".json";

        final ZomboidVersionMeta.AssetIndex assetIndex =
            LoomGradlePlugin.GSON.fromJson(getAssetsIndexJson().get(), ZomboidVersionMeta.AssetIndex.class);
        final File indexFile = new File(getAssetsDirectory().get().getAsFile(),
            "indexes" + File.separator + versionFile);

        final String json = getDownloadFactory()
            .download(Constants.INDEX_MANIFEST_PATH + versionFile)
            .sha1(assetIndex.sha1())
            .downloadString(indexFile.toPath());

        return LoomGradlePlugin.GSON.fromJson(json, AssetIndex.class);
    }

    @Nested
    protected abstract DownloadFactory getDownloadFactory();

    @Nested
    protected abstract CopyGameFileFactory getCopyGameFileFactory();

    private Path getAssetsPath(AssetIndex.Object object) {
        final String filename = "objects" + File.separator + object.path().replace("\\", File.separator);
        return new File(getAssetsDirectory().get().getAsFile(), filename).toPath();
    }
}

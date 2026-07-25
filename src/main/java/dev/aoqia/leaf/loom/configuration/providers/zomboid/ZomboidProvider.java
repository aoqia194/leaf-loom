/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2018-2025 FabricMC
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
import java.util.List;
import java.util.Objects;

import dev.aoqia.leaf.loom.configuration.providers.zomboid.assets.AssetIndex;
import dev.aoqia.leaf.loom.util.Constants;
import dev.aoqia.leaf.loom.util.MirrorUtil;

import dev.aoqia.leaf.loom.util.Platform;
import dev.aoqia.leaf.loom.util.gradle.GradleUtils;

import org.apache.commons.io.FilenameUtils;
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;

import dev.aoqia.leaf.loom.LoomGradleExtension;
import dev.aoqia.leaf.loom.api.mappings.layered.MappingsNamespace;
import dev.aoqia.leaf.loom.configuration.ConfigContext;
import dev.aoqia.leaf.loom.util.Check;

public class ZomboidProvider {
	private final ZomboidMetadataProvider metadataProvider;
	private final ConfigContext configContext;

	private File gameJar;

	public ZomboidProvider(ZomboidMetadataProvider metadataProvider, ConfigContext configContext) {
		this.metadataProvider = metadataProvider;
		this.configContext = configContext;

        if (isLegacyVersion()) {
            throw new RuntimeException("Complete JAR provider not supported for legacy PZ versions.");
        }
	}

	protected boolean provideClient() {
		return true;
	}

	protected boolean provideServer() {
		return true;
	}

	public void provide() throws Exception {
        if (!provideClient() || !provideServer()) {
            throw new UnsupportedOperationException("This provider only provides both the client and server!");
        }

        final int requiredMajorJavaVersion = getVersionInfo().javaVersion();
        final JavaVersion requiredJavaVersion = JavaVersion.toVersion(requiredMajorJavaVersion);

        if (!JavaVersion.current().isCompatibleWith(requiredJavaVersion)) {
            throw new IllegalStateException("Zomboid %s requires Java %s but Gradle is using %s"
                .formatted(gameVersion(), requiredJavaVersion, JavaVersion.current()));
        }

        Project project = getProject();
        setup(project);
//        validateFiles(project);

		final ZomboidLibraryProvider libraryProvider = new ZomboidLibraryProvider(this, project);
		libraryProvider.provide();
	}

    protected void setup(Project project) {
        Path gamePath = MirrorUtil.getGameJavaPath(project);
        this.gameJar = gamePath.resolve("projectzomboid.jar").toFile();

        // Find all other game lib jars in the root game folder and add them as compile libraries
//        project.getDependencies().add(Constants.Configurations.ZOMBOID_COMPILE_LIBRARIES,
//            project.fileTree(gamePath).include("*.jar").exclude("projectzomboid.jar", "pzexe.jar"));
    }

    public void validateFiles(Project project) throws IOException {
        Path gamePath = MirrorUtil.getGamePath(project);

        AssetIndex assetIndex = getClientAssetIndex();
        for (AssetIndex.Object object : assetIndex.getObjects()) {
            Path path = Path.of(FilenameUtils.separatorsToSystem(object.path()));
            String hash = object.hash();
            Path filePath = gamePath.resolve(path);

            // Disable game validation is an option to speed up on slow HDD
            if (!GradleUtils.getBooleanProperty(project, Constants.Properties.ENABLE_GAME_VALIDATION)) {
                continue;
            }

            if (!filePath.toFile().exists()) {
                throw new FileNotFoundException("Game file '%s' does not exist".formatted(filePath));
            }

            // TODO(leaf): Implement. See CopyGameFile#isHashValid
        }
    }

	public File workingDir() {
		return gameWorkingDirectory(configContext.project(), gameVersion());
	}

	public File dir(String path) {
		File dir = file(path);
		dir.mkdirs();
		return dir;
	}

	public File file(String path) {
		return new File(workingDir(), path);
	}

	public Path path(String path) {
		return file(path).toPath();
	}

	public File getGameJar() {
		Check.require(provideClient(), "Not configured to provide game jar");
		return gameJar;
	}

	public String gameVersion() {
		return Objects.requireNonNull(metadataProvider, "Metadata provider not setup").getZomboidVersion();
	}

	public ZomboidVersionMeta getVersionInfo() {
		return Objects.requireNonNull(metadataProvider, "Metadata provider not setup").getVersionMeta();
	}

    public AssetIndex getClientAssetIndex() {
        return Objects.requireNonNull(metadataProvider, "Metadata provider not setup").getClientAssetIndex();
    }

	/**
	 * @return true if the game version is older than 41.78.*
	 */
	public boolean isLegacyVersion() {
		return getVersionInfo().isLegacyVersion();
	}

	public List<Path> getGameJars() {
        return List.of(gameJar.toPath());
    }

	public MappingsNamespace getOfficialNamespace() {
        return MappingsNamespace.NAMED;
    }

	protected Project getProject() {
		return configContext.project();
	}

	protected LoomGradleExtension getExtension() {
		return configContext.extension();
	}

	public boolean refreshDeps() {
		return getExtension().refreshDeps();
	}

	public static File gameWorkingDirectory(Project project, String version) {
		LoomGradleExtension extension = LoomGradleExtension.get(project);
		File workingDir = new File(extension.getFiles().getUserCache(), version);
		workingDir.mkdirs();
		return workingDir;
	}
}

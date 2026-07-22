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
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.aoqia.leaf.loom.util.MirrorUtil;

import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.aoqia.leaf.loom.LoomGradleExtension;
import dev.aoqia.leaf.loom.api.mappings.layered.MappingsNamespace;
import dev.aoqia.leaf.loom.configuration.ConfigContext;
import dev.aoqia.leaf.loom.configuration.providers.BundleMetadata;
import dev.aoqia.leaf.loom.configuration.providers.zomboid.verify.ZomboidJarVerification;
import dev.aoqia.leaf.loom.configuration.providers.zomboid.verify.SignatureVerificationFailure;
import dev.aoqia.leaf.loom.util.Check;
import dev.aoqia.leaf.loom.util.Constants;
import dev.aoqia.leaf.loom.util.download.DownloadExecutor;
import dev.aoqia.leaf.loom.util.download.GradleDownloadProgressListener;
import dev.aoqia.leaf.loom.util.gradle.GradleUtils;
import dev.aoqia.leaf.loom.util.gradle.ProgressGroup;

public abstract class ZomboidProvider {
	private static final Logger LOGGER = LoggerFactory.getLogger(ZomboidProvider.class);

	private final ZomboidMetadataProvider metadataProvider;

	private File zomboidClientJar;
	private File zomboidServerJar;

	private final ConfigContext configContext;

	public ZomboidProvider(ZomboidMetadataProvider metadataProvider, ConfigContext configContext) {
		this.metadataProvider = metadataProvider;
		this.configContext = configContext;
	}

	protected boolean provideClient() {
		return true;
	}

	protected boolean provideServer() {
		return true;
	}

	public void provide() throws Exception {
		initFiles();

        final int requiredMajorJavaVersion = getVersionInfo().javaVersion();
        final JavaVersion requiredJavaVersion = JavaVersion.toVersion(requiredMajorJavaVersion);

        if (!JavaVersion.current().isCompatibleWith(requiredJavaVersion)) {
            throw new IllegalStateException("Zomboid %s requires Java %s but Gradle is using %s"
                .formatted(zomboidVersion(), requiredJavaVersion, JavaVersion.current()));
        }

        setup(getProject());

        if (!GradleUtils.getBooleanProperty(getProject(), Constants.Properties.ENABLE_GAME_VERIFICATION)) {
            LOGGER.info("Skipping game verification!");
        } else {
            verifyFiles();
        }

		final ZomboidLibraryProvider libraryProvider = new ZomboidLibraryProvider(this, configContext.project());
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

    private void setup(Project project) {
//        if (provideClient()) {
//            // Add discovered files to compile libraries (which also adds to runtime)
//            final Path gameInstallPath = MirrorUtil.getClientGamePath(project);
//            project.getDependencies().add(Constants.Configurations.ZOMBOID_CLIENT_COMPILE_LIBRARIES,
//                project.files(libsPath));
//        }
    }

    private void verifyFiles() {

    }

	private void verifyJars() throws IOException, SignatureVerificationFailure {
		if (!GradleUtils.getBooleanProperty(getProject(), Constants.Properties.ENABLE_GAME_VERIFICATION)) {
			LOGGER.info("Skipping game jar verification!");
			return;
		}

		LOGGER.info("Verifying Zomboid jars");

		ZomboidJarVerification verification = getProject().getObjects().newInstance(ZomboidJarVerification.class, zomboidVersion());

		if (provideClient()) {
			verification.verifyClientJar(zomboidClientJar.toPath());
		}

		if (provideServer()) {
            verification.verifyServerJar(zomboidServerJar.toPath());
		}

		LOGGER.info("Jar verification complete");
	}

	public File workingDir() {
		return zomboidWorkingDirectory(configContext.project(), zomboidVersion());
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

	public File getZomboidClientJar() {
		Check.require(provideClient(), "Not configured to provide client jar");
		return zomboidClientJar;
	}

	public File getZomboidServerJar() {
		Check.require(provideServer(), "Not configured to provide server jar");
		return zomboidServerJar;
	}

	public String zomboidVersion() {
		return Objects.requireNonNull(metadataProvider, "Metadata provider not setup").getZomboidVersion();
	}

	public ZomboidVersionMeta getVersionInfo() {
		return Objects.requireNonNull(metadataProvider, "Metadata provider not setup").getVersionMeta();
	}

	/**
	 * @return true if the game version is older than 41.78.*
	 */
	public boolean isLegacyVersion() {
		return getVersionInfo().isLegacyVersion();
	}

	/**
	 * Returns true if the minecraft version is between Beta 1.0 (inclusive) and 1.3 (exclusive),
	 * which splits the {@code official} mapping namespace into env-specific variants.
	 */
	public boolean isLegacySplitOfficialNamespaceVersion() {
		return getVersionInfo().isLegacySplitOfficialNamespaceVersion();
	}

	public abstract List<Path> getZomboidJars();

	public abstract MappingsNamespace getOfficialNamespace();

	protected Project getProject() {
		return configContext.project();
	}

	protected LoomGradleExtension getExtension() {
		return configContext.extension();
	}

	public boolean refreshDeps() {
		return getExtension().refreshDeps();
	}

	public static File zomboidWorkingDirectory(Project project, String version) {
		LoomGradleExtension extension = LoomGradleExtension.get(project);
		File workingDir = new File(extension.getFiles().getUserCache(), version);
		workingDir.mkdirs();
		return workingDir;
	}
}

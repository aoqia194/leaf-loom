/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2023 FabricMC
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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.jspecify.annotations.Nullable;

import dev.aoqia.leaf.loom.LoomGradleExtension;
import dev.aoqia.leaf.loom.LoomGradlePlugin;
import dev.aoqia.leaf.loom.configuration.ConfigContext;
import dev.aoqia.leaf.loom.configuration.DependencyInfo;
import dev.aoqia.leaf.loom.configuration.providers.zomboid.ManifestLocations.ManifestLocation;
import dev.aoqia.leaf.loom.util.Constants;
import dev.aoqia.leaf.loom.util.download.DownloadBuilder;

public final class ZomboidMetadataProvider {
	private final Options options;
	private final Function<String, DownloadBuilder> download;

	private ManifestEntryLocation versionEntry;
	private ZomboidVersionMeta versionMeta;

	private ZomboidMetadataProvider(Options options, Function<String, DownloadBuilder> download) {
		this.options = options;
		this.download = download;
	}

	public static ZomboidMetadataProvider create(ConfigContext configContext) {
		final String version = resolveZomboidVersion(configContext.project());

		return new ZomboidMetadataProvider(
            ZomboidMetadataProvider.Options.create(version, configContext.project()),
            configContext.extension()::download
		);
	}

	private static String resolveZomboidVersion(Project project) {
		final DependencyInfo dependency = DependencyInfo.create(project, Constants.Configurations.ZOMBOID);
		return dependency.getDependency().getVersion();
	}

	public String getZomboidVersion() {
		return options.zomboidVersion();
	}

	public ZomboidVersionMeta getVersionMeta() {
		try {
			if (versionEntry == null) {
				versionEntry = getVersionEntry();
			}

			if (versionMeta == null) {
				versionMeta = readVersionMeta();
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e.getMessage(), e);
		}

		return versionMeta;
	}

	private ManifestEntryLocation getVersionEntry() throws IOException {
		// Custom URL always takes priority
		if (options.customManifestUrl() != null) {
			VersionsManifest.Version customVersion = new VersionsManifest.Version();
			customVersion.url = options.customManifestUrl();
			return new ManifestEntryLocation(null, options.zomboidVersion(), customVersion);
		}

		final List<ManifestEntrySupplier> suppliers = new ArrayList<>();

		// First try finding the version with caching
		for (ManifestLocation location : options.versionsManifests()) {
			suppliers.add(() -> getManifestEntry(location, false));
		}

		// Then force download the manifest to find the version
		for (ManifestLocation location : options.versionsManifests()) {
			suppliers.add(() -> getManifestEntry(location, true));
		}

		for (ManifestEntrySupplier supplier : suppliers) {
			final ManifestEntryLocation version = supplier.get();

			if (version != null) {
				return version;
			}
		}

		throw new RuntimeException("Failed to find Zomboid version: " + options.zomboidVersion());
	}

	private ManifestEntryLocation getManifestEntry(ManifestLocation location, boolean forceDownload) throws IOException {
		DownloadBuilder builder = download.apply(location.url());

		if (forceDownload) {
			builder = builder.forceDownload();
		} else {
			builder = builder.defaultCache();
		}

		final Path cacheFile = location.cacheFile(options.userCache());
		final String versionManifest = builder.downloadString(cacheFile);
		final VersionsManifest manifest = LoomGradlePlugin.GSON.fromJson(versionManifest, VersionsManifest.class);
		final VersionsManifest.Version version = manifest.getVersion(options.zomboidVersion());

		if (version != null) {
			return new ManifestEntryLocation(location, options.zomboidVersion(), version);
		}

		return null;
	}

	private ZomboidVersionMeta readVersionMeta() throws IOException {
		final DownloadBuilder builder = download.apply(versionEntry.entry.url);

		if (versionEntry.entry.hash != null) {
			builder.sha1(versionEntry.entry.hash);
		} else {
			builder.defaultCache();
		}

		final String fileName = getVersionMetaFileName();
		final Path cacheFile = options.workingDir().resolve(fileName);
		final String json = builder.downloadString(cacheFile);
		return LoomGradlePlugin.GSON.fromJson(json, ZomboidVersionMeta.class);
	}

	private String getVersionMetaFileName() {
		// custom version metadata
		if (versionEntry.manifest == null) {
			return "zomboid_info_" + Integer.toHexString(versionEntry.entry.url.hashCode()) + ".json";
		}

		// metadata url taken from versions manifest
		return versionEntry.manifest.name() + "_version_info.json";
	}

	public record Options(String zomboidVersion,
					ManifestLocations versionsManifests,
					@Nullable String customManifestUrl,
					Path userCache,
					Path workingDir) {
		public static Options create(String version, Project project) {
			final LoomGradleExtension extension = LoomGradleExtension.get(project);
			final Path userCache = extension.getFiles().getUserCache().toPath();
			final Path workingDir = ZomboidProvider.zomboidWorkingDirectory(project, version).toPath();

			final ManifestLocations manifestLocations = extension.getVersionsManifests();
			final Property<String> customMetaUrl = extension.getCustomZomboidMetadata();

			return new Options(
					version,
					manifestLocations,
					customMetaUrl.getOrNull(),
					userCache,
					workingDir
			);
		}
	}

	@FunctionalInterface
	private interface ManifestEntrySupplier {
		ManifestEntryLocation get() throws IOException;
	}

	private record ManifestEntryLocation(ManifestLocation manifest, String entryKey, VersionsManifest.Version entry) {
	}
}

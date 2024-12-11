/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021 FabricMC
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import com.google.common.collect.Sets;
import net.aoqia.loom.util.Constants;
import net.aoqia.loom.util.FileSystemUtil;
import net.aoqia.loom.util.JarUtil;

public class ZomboidJarSplitter implements AutoCloseable {
    private final Path clientInputJar;
    private final Path serverInputJar;
    private final Set<String> sharedEntries = new HashSet<>();
    private final Set<String> forcedClientEntries = new HashSet<>();
    private EntryData entryData;

    public ZomboidJarSplitter(Path clientInputJar, Path serverInputJar) {
        this.clientInputJar = Objects.requireNonNull(clientInputJar);
        this.serverInputJar = Objects.requireNonNull(serverInputJar);
    }

    public void split(Path clientOnlyOutputJar, Path commonOutputJar) throws IOException {
        Objects.requireNonNull(clientOnlyOutputJar);
        Objects.requireNonNull(commonOutputJar);

        if (entryData == null) {
            entryData = new EntryData(JarUtil.getJarEntries(clientInputJar), JarUtil.getJarEntries(serverInputJar));
        }

        // Not something we expect, will require 3 jars, server, client and common.
        assert entryData.serverOnlyEntries.isEmpty();

        copyEntriesToJar(entryData.commonEntries, serverInputJar, commonOutputJar, "common");
        copyEntriesToJar(entryData.clientOnlyEntries, clientInputJar, clientOnlyOutputJar, "client");
    }

    private void createManifest(FileSystemUtil.Delegate outputFs, String env) throws IOException {
        final Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue(Constants.Manifest.SPLIT_ENV_NAME, env);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        manifest.write(out);
        Files.createDirectories(outputFs.get().getPath("META-INF"));
        Files.write(outputFs.get().getPath(Constants.Manifest.PATH), out.toByteArray());
    }

    // A duplicate from JarUtil for env specific manifest.
    private void copyEntriesToJar(Set<String> entries, Path inputJar, Path outputJar, String env) throws
        IOException {
        Files.deleteIfExists(outputJar);

        try (FileSystemUtil.Delegate inputFs = FileSystemUtil.getJarFileSystem(inputJar);
             FileSystemUtil.Delegate outputFs = FileSystemUtil.getJarFileSystem(outputJar, true)) {
            for (String entry : entries) {
                Path inputPath = inputFs.get().getPath(entry);
                Path outputPath = outputFs.get().getPath(entry);
                assert Files.isRegularFile(inputPath);

                Path outputPathParent = outputPath.getParent();
                if (outputPathParent != null) {
                    Files.createDirectories(outputPathParent);
                }

                Files.copy(inputPath, outputPath, StandardCopyOption.COPY_ATTRIBUTES);
            }

            createManifest(outputFs, env);
        }
    }

    public void sharedEntry(String path) {
        this.sharedEntries.add(path);
    }

    public void forcedClientEntry(String path) {
        this.forcedClientEntries.add(path);
    }

    @Override
    public void close() throws Exception {
    }

    private final class EntryData {
        private final Set<String> clientEntries;
        private final Set<String> serverEntries;
        private final Set<String> commonEntries;
        private final Set<String> clientOnlyEntries;
        private final Set<String> serverOnlyEntries;

        private EntryData(Set<String> clientEntries, Set<String> serverEntries) {
            this.clientEntries = clientEntries;
            this.serverEntries = serverEntries;

            this.commonEntries = Sets.newHashSet(clientEntries);
            this.commonEntries.retainAll(serverEntries);
            this.commonEntries.addAll(sharedEntries);
            this.commonEntries.removeAll(forcedClientEntries);

            this.clientOnlyEntries = Sets.newHashSet(clientEntries);
            this.clientOnlyEntries.removeAll(serverEntries);
            this.clientOnlyEntries.addAll(sharedEntries);
            this.clientOnlyEntries.addAll(forcedClientEntries);

            this.serverOnlyEntries = Sets.newHashSet(serverEntries);
            this.serverOnlyEntries.removeAll(clientEntries);
        }
    }
}

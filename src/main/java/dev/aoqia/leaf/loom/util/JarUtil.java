/*
 * This file is part of leaf-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2024 aoqia, FabricMC
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
package dev.aoqia.leaf.loom.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import com.google.common.collect.Sets;
import org.jetbrains.annotations.Nullable;

public class JarUtil {
    /**
     * Collects all the entries in a JAR.
     *
     * @param jar Path to the JAR file.
     * @return A set of JAR entries.
     *
     * @throws IOException
     */
    public static Set<String> getJarEntries(Path jar) throws IOException {
        Set<String> entries = Sets.newHashSet();

        try (var fs = FileSystemUtil.getJarFileSystem(jar);
             var walk = Files.walk(fs.get().getPath("/"))) {
            for (var iterator = walk.iterator(); iterator.hasNext(); ) {
                final Path entry = iterator.next();
                if (!Files.isRegularFile(entry)) {
                    continue;
                }

                String entryPath = fs.get().getPath("/").relativize(entry).toString();
                entries.add(entryPath);
            }
        }

        return entries;
    }

    /**
     * Collects all the entries in a JAR and their hashes.
     *
     * @param jar Path to the JAR file.
     * @return A map of JAR entries with hashes.
     */
    public static Map<String, String> getJarEntriesWithHashes(Path jar) throws IOException {
        Map<String, String> entries = new HashMap<>();

        try (var fs = FileSystemUtil.getJarFileSystem(jar);
             var walk = Files.walk(fs.get().getPath("/"))) {
            for (var iterator = walk.iterator(); iterator.hasNext(); ) {
                final Path entry = iterator.next();
                if (!Files.isRegularFile(entry)) {
                    continue;
                }

                final Path entryPath = fs.get().getPath("/").relativize(entry);
                final Optional<String> hash = AttributeHelper.readAttribute(entryPath, "LoomHash");

                if (entryPath.toString().endsWith(".att")) {
                    continue;
                }

                entries.put(entryPath.toString(), hash.orElse(""));
            }
        }

        return entries;
    }

    /**
     * Copies entries from one JAR file to another.
     *
     * @param entries Entries to copy.
     * @param inputJar The input JAR file path.
     * @param outputJar The output JAR file path.
     * @param env The environment that the output JAR belongs to.
     * @throws IOException
     */
    public static void copyEntriesToJar(Set<String> entries, Path inputJar, Path outputJar,
        @Nullable String env) throws IOException {
        Files.deleteIfExists(outputJar);

        try (var inputFs = FileSystemUtil.getJarFileSystem(inputJar);
             var outputFs = FileSystemUtil.getJarFileSystem(outputJar, true)) {
            for (String entry : entries) {
                final Path inputPath = inputFs.get().getPath(entry);
                final Path outputPath = outputFs.get().getPath(entry);
                if (!Files.isRegularFile(inputPath)) {
                    continue;
                }

                Path outputPathParent = outputPath.getParent();
                if (outputPathParent != null) {
                    Files.createDirectories(outputPathParent);
                }

                Files.copy(inputPath, outputPath, StandardCopyOption.COPY_ATTRIBUTES);
            }

            JarUtil.createManifest(outputFs, env);
        }
    }

    /**
     * Copies entries from one JAR file to another.
     *
     * @param entries Entries to copy.
     * @param inputJar The input JAR file path.
     * @param outputJar The output JAR file path.
     * @throws IOException
     */
    public static void copyEntriesToJar(Set<String> entries, Path inputJar, Path outputJar) throws
        IOException {
        copyEntriesToJar(entries, inputJar, outputJar, null);
    }

    /**
     * A simple utility function to create a manifest for a JAR file.
     *
     * @param outputFs The JAR file system to write to.
     * @param env The environment that the JAR belongs in.
     * @throws IOException
     */
    public static void createManifest(FileSystemUtil.Delegate outputFs, String env) throws
        IOException {
        final Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");

        if (env != null) {
            manifest.getMainAttributes().putValue(Constants.Manifest.SPLIT_ENV_NAME, env);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        manifest.write(out);

        Files.createDirectories(outputFs.get().getPath("META-INF"));
        Files.write(outputFs.get().getPath(Constants.Manifest.PATH), out.toByteArray());
    }

    /**
     * A simple utility function to create a manifest for a JAR file.
     *
     * @param outputFs The JAR file system to write to.
     * @throws IOException
     */
    public static void createManifest(FileSystemUtil.Delegate outputFs) throws IOException {
        createManifest(outputFs, null);
    }
}

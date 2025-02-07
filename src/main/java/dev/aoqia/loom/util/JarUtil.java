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
package dev.aoqia.loom.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Stream;

import com.google.common.collect.Sets;

public class JarUtil {
    /**
     * Collects all the entries in a JAR.
     *
     * @param input Path to the JAR file.
     * @return A set of JAR entries.
     *
     * @throws IOException
     */
    public static Set<String> getJarEntries(Path input) throws IOException {
        Set<String> entries = Sets.newHashSet();

        try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(input);
             Stream<Path> walk = Files.walk(fs.get().getPath("/"))) {
            Iterator<Path> iterator = walk.iterator();

            while (iterator.hasNext()) {
                Path fsPath = iterator.next();

                if (!Files.isRegularFile(fsPath)) {
                    continue;
                }

                String entryPath = fs.get().getPath("/").relativize(fsPath).toString();

                if (entryPath.startsWith("META-INF/")) {
                    continue;
                }

                entries.add(entryPath);
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
     * @throws IOException
     */
    public static void copyEntriesToJar(Set<String> entries, Path inputJar, Path outputJar) throws
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

            JarUtil.createManifest(outputFs);
        }
    }

    /**
     * A simple utility function to create a manifest for a JAR file.
     *
     * @param outputFs The JAR file system to write to.
     * @throws IOException
     */
    public static void createManifest(FileSystemUtil.Delegate outputFs) throws IOException {
        final Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        manifest.write(out);
        Files.createDirectories(outputFs.get().getPath("META-INF"));
        Files.write(outputFs.get().getPath(Constants.Manifest.PATH), out.toByteArray());
    }
}

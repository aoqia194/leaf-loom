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
package dev.aoqia.leaf.loom.decompilers.cache;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

import dev.aoqia.leaf.loom.util.Checksum;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @param name The class name
 * @param innerClasses A list of inner class names
 * @param superClasses A list of parent classes (super and interface) from the class and all inner
 * classes
 */
public record ClassEntry(String name, List<String> innerClasses, List<String> superClasses) {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClassEntry.class);

    public ClassEntry {
        if (!name.endsWith(".class")) {
            throw new IllegalArgumentException("Class name must end with '.class': " + name);
        }

        if (!name.contains("/")) {
            throw new IllegalArgumentException("Class name must be in a package: " + name);
        }


        String className = name.replace(".class", "");

        for (String innerClass : innerClasses) {
            if (!innerClass.endsWith(".class")) {
                throw new IllegalArgumentException(
                    "Inner class name must end with '.class': " + name);
            }
            if (!innerClass.startsWith(className)) {
                throw new IllegalArgumentException("Inner class (" + innerClass +
                                                   ") does not have the parent class name as a " +
                                                   "prefix: " +
                                                   name);
            }
        }

        for (String superClass : superClasses) {
            if (!superClass.endsWith(".class")) {
                throw new IllegalArgumentException(
                    "Super class name must end with '.class': " + superClass);
            }
        }
    }

    private void copy(Path source, Path target) throws IOException {
        LOGGER.debug("Copying class entry `{}` from `{}` to `{}`", name, source, target);
        Files.copy(source, target);
    }

    /**
     * Copy the class and its inner classes to the target root.
     *
     * @param sourceRoot The root of the source jar
     * @param targetRoot The root of the target jar
     * @throws IOException If an error occurs while copying the files
     */
    public void copyTo(Path sourceRoot, Path targetRoot) throws IOException {
        Path targetPath = targetRoot.resolve(name);
        Files.createDirectories(targetPath.getParent());
        copy(sourceRoot.resolve(name), targetPath);

        for (String innerClass : innerClasses) {
            copy(sourceRoot.resolve(innerClass), targetRoot.resolve(innerClass));
        }
    }

    /**
     * Hash the class and its inner classes using sha256.
     *
     * @param root The root of the jar
     * @return The hash of the class and its inner classes
     *
     * @throws IOException If an error occurs while hashing the files
     */
    public String hash(Path root) throws IOException {
        StringJoiner joiner = new StringJoiner(",");

        joiner.add(Checksum.sha256Hex(Files.readAllBytes(root.resolve(name))));

        for (String innerClass : innerClasses) {
            joiner.add(Checksum.sha256Hex(Files.readAllBytes(root.resolve(innerClass))));
        }

        return Checksum.sha256Hex(joiner.toString().getBytes());
    }

    /**
     * Return a hash of the class and its super classes.
     */
    public String hashSuperHierarchy(Map<String, String> hashes) throws IOException {
        final String selfHash = Objects.requireNonNull(hashes.get(name),
            "Hash for own class not found");

        if (superClasses.isEmpty()) {
            return selfHash;
        }

        StringJoiner joiner = new StringJoiner(",");
        joiner.add(selfHash);

        for (String superClass : superClasses) {
            final String superHash = hashes.get(superClass);

            if (superHash != null) {
                joiner.add(superHash);
            } else if (!superClass.startsWith("java/")) {
                // This will happen if the super class is not part of the input jar
                LOGGER.debug("Hash for super class {} of {} not found", superClass, name);
            }
        }

        return Checksum.sha256Hex(joiner.toString().getBytes());
    }

    public String sourcesFileName() {
        return name.replace(".class", ".java");
    }
}

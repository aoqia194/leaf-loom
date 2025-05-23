/*
 * This file is part of leaf-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2022 aoqia, FabricMC
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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.util.Optional;

public final class AttributeHelper {
    private AttributeHelper() {}

    public static Optional<String> readAttribute(Path path, String key) throws IOException {
        final Path attributesFile = getFallbackPath(path, key);

        if (exists(attributesFile)) {
            // Use the fallback file if it exists.
            return Optional.of(Files.readString(attributesFile, StandardCharsets.UTF_8));
        }

        try {
            final UserDefinedFileAttributeView attributeView;

            try {
                attributeView = Files.getFileAttributeView(path,
                    UserDefinedFileAttributeView.class);
            } catch (UnsupportedOperationException ignored) {
                throw new FileSystemException("AttributeView was not supported, probably ZipFs.");
            }

            if (attributeView == null) {
                throw new FileSystemException("AttributeView was null");
            }

            if (!attributeView.list().contains(key)) {
                return Optional.empty();
            }

            final ByteBuffer buffer = ByteBuffer.allocate(attributeView.size(key));
            attributeView.read(key, buffer);
            buffer.flip();
            final String value = StandardCharsets.UTF_8.decode(buffer).toString();
            return Optional.of(value);
        } catch (FileSystemException ignored) {
            return Optional.empty();
        }
    }

    public static void writeAttribute(Path path, String key, String value) throws IOException {
        final Path attributesFile = getFallbackPath(path, key);

        try {
            final UserDefinedFileAttributeView attributeView;

            // Try-catch required due to getFileAttributeView going crazy when writing to zip/jar.
            try {
                attributeView = Files.getFileAttributeView(path,
                    UserDefinedFileAttributeView.class);
            } catch (UnsupportedOperationException ignored) {
                throw new FileSystemException("AttributeView was not supported, probably ZipFs.");
            }

            if (attributeView == null) {
                throw new FileSystemException("AttributeView was null");
            }

            final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            final ByteBuffer buffer = ByteBuffer.wrap(bytes);
            final int written = attributeView.write(key, buffer);
            assert written == bytes.length;

            if (exists(attributesFile)) {
                Files.delete(attributesFile);
            }
        } catch (FileSystemException ignored) {
            // Fallback to a separate file when using a file system that does not attributes.
            Files.writeString(attributesFile, value, StandardCharsets.UTF_8);
        }
    }

    private static Path getFallbackPath(Path path, String key) {
        return path.resolveSibling(path.getFileName() + "." + key + ".att");
    }

    // A faster exists check
    private static boolean exists(Path path) {
        return path.getFileSystem() == FileSystems.getDefault() ? path.toFile().exists()
            : Files.exists(path);
    }
}

/*
 * This file is part of leaf-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2022-2024 aoqia, FabricMC
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
package dev.aoqia.loom.util.copygamefile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntConsumer;

import dev.aoqia.loom.util.AttributeHelper;
import dev.aoqia.loom.util.Checksum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CopyGameFile {
    private static final Logger LOGGER = LoggerFactory.getLogger(CopyGameFile.class);
    private final Path path;
    private final String expectedHash;
    private final Duration maxAge;
    private final boolean forced;
    private final int attempt;
    private final CopyGameFileProgressListener progressListener;

    CopyGameFile(Path path,
        String expectedHash,
        Duration maxAge,
        boolean forced,
        int attempt,
        CopyGameFileProgressListener progressListener) {
        this.path = path;
        this.expectedHash = expectedHash;
        this.maxAge = maxAge;
        this.forced = forced;
        this.attempt = attempt;
        this.progressListener = progressListener;
    }

    public static CopyGameFileBuilder create(Path path) {
        return CopyGameFileBuilder.create(path);
    }

    /**
     * This function is faster than the normal exists check.
     *
     * @param path Path to check.
     * @return True if the file exists.
     */
    private static boolean exists(Path path) {
        return path.getFileSystem() == FileSystems.getDefault() ? path.toFile().exists() : Files.exists(path);
    }

    private void copyGameFileToPath(Path output) throws IOException {
        // Copy the file initially to a .part file
        final Path partFile = getPartFile(output);

        try {
            Files.deleteIfExists(output);
            Files.deleteIfExists(partFile);
        } catch (IOException e) {
            throw error(e, "Failed to delete existing file");
        }

        final long length = this.path.toFile().length();
        AtomicLong totalBytes = new AtomicLong(0);

        try (InputStream inputStream = Files.newInputStream(this.path, StandardOpenOption.READ);
             OutputStream outputStream = Files.newOutputStream(partFile, StandardOpenOption.CREATE_NEW)) {
            copyWithCallback(inputStream, outputStream, value -> {
                progressListener.onProgress(totalBytes.addAndGet(value), length);
            });
        } catch (IOException e) {
            throw error(e, "Failed to write game file output");
        }

        if (Files.notExists(partFile)) {
            throw error("No file was copied!!");
        }

        if (length > 0) {
            try {
                final long actualLength = Files.size(partFile);

                if (actualLength != length) {
                    throw error(
                        "Unexpected file length of %d bytes, expected %d bytes".formatted(actualLength, length));
                }
            } catch (IOException e) {
                throw error(e);
            }
        }

        try {
            // Once the file has been fully read, move it to the destination file.
            // This ensures that the output file only exists in fully populated state.
            Files.move(partFile, output);
            //            Files.move(partFile, output, StandardCopyOption.COPY_ATTRIBUTES);
        } catch (IOException e) {
            throw error(e, "Failed to complete copy of game file");
        }
    }

    private void copyWithCallback(InputStream is, OutputStream os, IntConsumer consumer) throws IOException {
        byte[] buffer = new byte[1024];
        int length;

        while ((length = is.read(buffer)) > 0) {
            os.write(buffer, 0, length);
            consumer.accept(length);
        }
    }

    private Path getPartFile(Path output) {
        return output.resolveSibling(output.getFileName() + ".part");
    }

    private IOException error(Throwable throwable) {
        return new IOException(throwable);
    }

    private boolean requiresCopy(Path output) throws IOException {
        final boolean locked = getAndResetLock(output);

        if (forced || !exists(output)) {
            // File does not exist, or we are forced to download again.
            return true;
        }

        if (locked && attempt == 1) {
            LOGGER.warn(
                "Forcing copy of game file {} as existing lock file was found. This may happen if the gradle build " +
                "was " +
                "forcefully canceled.",
                output);
            return true;
        }

        if (expectedHash != null) {
            final String hashAttribute = readHash(output).orElse("");

            if (expectedHash.equalsIgnoreCase(hashAttribute)) {
                // File has a matching hash attribute, assume file intact.
                return false;
            }

            if (isHashValid(output)) {
                // Valid hash, no need to re-download
                writeHash(output, expectedHash);
                return false;
            }

            LOGGER.info("Found existing file ({}) to copy with unexpected hash.", output);
        }

        //noinspection RedundantIfStatement
        if (!maxAge.equals(Duration.ZERO) && !isOutdated(output)) {
            return false;
        }

        // Default to re-downloading, may check the etag
        return true;
    }

    private boolean isHashValid(Path path) {
        int i = expectedHash.indexOf(':');
        String algorithm = expectedHash.substring(0, i);
        String hash = expectedHash.substring(i + 1);

        try {
            if (!algorithm.equals("sha1")) {
                throw error("Unsupported hash algorithm (%s)", algorithm);
            }

            return Checksum.sha1Hex(path).equalsIgnoreCase(hash);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private boolean isOutdated(Path path) throws IOException {
        try {
            final FileTime lastModified = Files.getLastModifiedTime(path);
            return lastModified.toInstant().isBefore(Instant.now().minus(maxAge));
        } catch (IOException e) {
            throw error(e, "Failed to check if (%s) is outdated", path);
        }
    }

    private Optional<String> readHash(Path output) {
        try {
            return AttributeHelper.readAttribute(output, "LoomHash");
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private void writeHash(Path output, String value) throws IOException {
        try {
            AttributeHelper.writeAttribute(output, "LoomHash", value);
        } catch (IOException e) {
            throw error(e, "Failed to write hash to (%s)", output);
        }
    }

    private boolean getAndResetLock(Path output) throws IOException {
        final Path lock = getLockFile(output);
        final boolean exists = exists(lock);

        if (exists) {
            try {
                Files.delete(lock);
            } catch (IOException e) {
                throw error(e, "Failed to release lock on %s", lock);
            }
        }

        return exists;
    }

    private Path getLockFile(Path output) {
        return output.resolveSibling(output.getFileName() + ".lock");
    }

    private void tryCleanup(Path output) {
        try {
            Files.deleteIfExists(output);
        } catch (IOException ignored) {
            // ignored
        }

        try {
            Files.deleteIfExists(getLockFile(output));
        } catch (IOException ignored) {
            // ignored
        }

        try {
            Files.deleteIfExists(getPartFile(output));
        } catch (IOException ignored) {
            // ignored
        }
    }

    private void createLock(Path output) throws IOException {
        final Path lock = getLockFile(output);

        try {
            Files.createFile(lock);
        } catch (IOException e) {
            throw error(e, "Failed to acquire lock on %s", lock);
        }
    }

    void copyGameFile(Path output) throws IOException {
        boolean required = requiresCopy(output);

        if (!required) {
            // Does not require download, we are done here.
            progressListener.onEnd();
            return;
        }

        try {
            doCopyGameFile(output);
        } catch (Throwable throwable) {
            tryCleanup(output);
            throw error(throwable, "Failed to copy file from (%s) to (%s)", path, output);
        } finally {
            progressListener.onEnd();
        }
    }

    private void doCopyGameFile(Path output) throws IOException {
        try {
            Files.createDirectories(output);
        } catch (IOException e) {
            throw error(e, "Failed to create parent directories");
        }

        // Create a .lock file, this allows us to re-download if the download was forcefully aborted part way through.
        createLock(output);
        progressListener.onStart();
        getAndResetLock(output);

        if (exists(output) && isOutdated(output)) {
            try {
                // Update the last modified time so we don't retry the request until the max age has passed again.
                Files.setLastModifiedTime(output, FileTime.from(Instant.now()));
            } catch (IOException e) {
                throw error(e, "Failed to update last modified time");
            }
        }

        copyGameFileToPath(output);

        if (expectedHash == null) {
            return;
        }

        // Ensure we downloaded the expected hash.
        if (!isHashValid(output)) {
            String actualHash;

            try {
                actualHash = Checksum.sha1Hex(output);
                Files.deleteIfExists(output);
            } catch (IOException e) {
                actualHash = "unknown hash";
            }

            throw error("Failed to copy (%s) with expected hash: %s got %s",
                path,
                expectedHash,
                actualHash);
        }

        // Write the hash to the file attribute.
        // This saves a lot of time trying to re-compute the hash when re-visiting this file.
        writeHash(output, expectedHash);
    }

    private IOException error(String message, Object... args) {
        return new IOException(String.format(Locale.ENGLISH, message, args));
    }

    private IOException error(Throwable throwable, String message, Object... args) {
        return new IOException(message.formatted(args), throwable);
    }
}

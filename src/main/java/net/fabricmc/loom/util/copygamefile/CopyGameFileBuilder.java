/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2022 FabricMC
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

package net.fabricmc.loom.util.copygamefile;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;

@SuppressWarnings("UnusedReturnValue")
public class CopyGameFileBuilder {
    private static final Duration ONE_DAY = Duration.ofDays(1);

    private final Path path;
    private String expectedHash = null;
    private boolean forced = false;
    private Duration maxAge = Duration.ZERO;
    private CopyGameFileProgressListener progressListener = CopyGameFileProgressListener.NONE;
    private int maxRetries = 3;

    private CopyGameFileBuilder(Path path) {
        this.path = path;
    }

    static CopyGameFileBuilder create(Path path) {
        return new CopyGameFileBuilder(path);
    }

    public CopyGameFileBuilder sha1(String sha1) {
        this.expectedHash = "sha1:" + sha1;
        return this;
    }

    public CopyGameFileBuilder forced() {
        forced = true;
        return this;
    }

    public CopyGameFileBuilder progress(CopyGameFileProgressListener progressListener) {
        this.progressListener = progressListener;
        return this;
    }

    public CopyGameFileBuilder maxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
        return this;
    }

    public CopyGameFileBuilder defaultCache() {
        return maxAge(ONE_DAY);
    }

    public CopyGameFileBuilder maxAge(Duration duration) {
        this.maxAge = duration;
        return this;
    }

    public void copyGameFileFromPathAsync(Path path, CopyGameFileExecutor executor) {
        executor.runAsync(() -> copyGameFileFromPath(path));
    }

    public void copyGameFileFromPath(Path path) throws IOException {
        withRetries((copyGameFile) -> {
            copyGameFile.copyGameFile(path);
            return null;
        });
    }

    private <T> T withRetries(CopyGameFileFn<T> supplier) throws IOException {
        for (int i = 1; i <= maxRetries; i++) {
            try {
                return supplier.get(build(i));
            } catch (IOException e) {
                if (i == maxRetries) {
                    throw new IOException(String.format(Locale.ENGLISH,
                        "Failed copy after %d attempts",
                        maxRetries), e);
                }
            }
        }

        throw new IllegalStateException();
    }

    private CopyGameFile build(int attempt) {
        return new CopyGameFile(
            this.path,
            this.expectedHash,
            this.maxAge,
            this.forced,
            attempt,
            this.progressListener);
    }

    @FunctionalInterface
    private interface CopyGameFileFn<T> {
        T get(CopyGameFile copyGameFile) throws IOException;
    }
}

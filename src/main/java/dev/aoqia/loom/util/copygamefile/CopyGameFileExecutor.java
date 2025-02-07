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
package dev.aoqia.loom.util.copygamefile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class CopyGameFileExecutor implements AutoCloseable {
    private final ExecutorService executorService;
    private final List<IOException> copyExceptions = Collections.synchronizedList(new ArrayList<>());

    public CopyGameFileExecutor(int threads) {
        executorService = Executors.newFixedThreadPool(threads);
    }

    void runAsync(CopyGameFileRunner copyGameFileRunner) {
        if (!copyExceptions.isEmpty()) {
            return;
        }

        executorService.execute(() -> {
            try {
                copyGameFileRunner.run();
            } catch (IOException e) {
                executorService.shutdownNow();
                copyExceptions.add(e);
                throw new UncheckedIOException(e);
            }
        });
    }

    @Override
    public void close() throws IOException {
        executorService.shutdown();

        try {
            executorService.awaitTermination(1, TimeUnit.DAYS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        if (!copyExceptions.isEmpty()) {
            IOException copyException = new IOException("Failed to copy");

            for (IOException suppressed : copyExceptions) {
                copyException.addSuppressed(suppressed);
            }

            throw copyException;
        }
    }

    @FunctionalInterface
    public interface CopyGameFileRunner {
        void run() throws IOException;
    }
}

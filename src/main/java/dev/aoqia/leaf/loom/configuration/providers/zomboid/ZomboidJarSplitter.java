/*
 * This file is part of leaf-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021 aoqia, FabricMC
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
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import dev.aoqia.leaf.loom.util.JarUtil;

import com.google.common.collect.Sets;

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
            entryData = new EntryData(JarUtil.getJarEntries(clientInputJar),
                JarUtil.getJarEntries(serverInputJar));
        }

        // Not something we expect, will require 3 jars, server, client and common.
        assert entryData.serverOnlyEntries.isEmpty();

        JarUtil.copyEntriesToJar(entryData.commonEntries, serverInputJar, commonOutputJar,
            "common");
        JarUtil.copyEntriesToJar(entryData.clientOnlyEntries, clientInputJar, clientOnlyOutputJar,
            "client");
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
        private final Set<String> commonEntries;
        private final Set<String> clientOnlyEntries;
        private final Set<String> serverOnlyEntries;

        private EntryData(Set<String> clientEntries, Set<String> serverEntries) {
            // TODO: Store hashes in value part of entries hashmap, check the hash in a filter func.
            //   For example when checking the client only entries, if there are any files in
            //   both client and server that have different hashes from common, put them in client.
            //   This may be needed in the future if client and server code becomes different a lot.

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

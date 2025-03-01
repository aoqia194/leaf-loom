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
package dev.aoqia.leaf.loom.configuration.providers.zomboid;

import java.io.File;
import java.nio.file.Path;
import java.util.Objects;

public abstract sealed class ZomboidJar
    permits ZomboidJar.Client,
    ZomboidJar.ClientOnly,
    ZomboidJar.Common,
    ZomboidJar.Merged,
    ZomboidJar.Server {
    private final Path path;
    private final boolean merged, client, server;
    private final Type type;

    protected ZomboidJar(Path path, boolean merged, boolean client, boolean server, Type type) {
        this.path = Objects.requireNonNull(path);
        this.merged = merged;
        this.client = client;
        this.server = server;
        this.type = type;
    }

    public File toFile() {
        return getPath().toFile();
    }

    public Path getPath() {
        return path;
    }

    public boolean isMerged() {
        return merged;
    }

    public boolean includesClient() {
        return client;
    }

    public boolean includesServer() {
        return server;
    }

    public String getName() {
        return type.toString();
    }

    public Type getType() {
        return type;
    }

    public abstract ZomboidJar forPath(Path path);

    public enum Type {
        // Merged jar
        MERGED("merged"),

        // Regular jars, not merged or split
        SERVER("server"),
        CLIENT("client"),

        // Split jars
        COMMON("common"),
        CLIENT_ONLY("clientOnly");

        private final String name;

        Type(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public static final class Merged extends ZomboidJar {
        public Merged(Path path) {
            super(path, true, true, true, Type.MERGED);
        }

        @Override
        public ZomboidJar forPath(Path path) {
            return new Merged(path);
        }
    }

    public static final class Common extends ZomboidJar {
        public Common(Path path) {
            super(path, false, false, true, Type.COMMON);
        }

        @Override
        public ZomboidJar forPath(Path path) {
            return new Common(path);
        }
    }

    public static final class Server extends ZomboidJar {
        public Server(Path path) {
            super(path, false, false, true, Type.SERVER);
        }

        @Override
        public ZomboidJar forPath(Path path) {
            return new Server(path);
        }
    }

    // Un-split client jar
    public static final class Client extends ZomboidJar {
        public Client(Path path) {
            super(path, false, true, false, Type.CLIENT);
        }

        @Override
        public ZomboidJar forPath(Path path) {
            return new Client(path);
        }
    }

    // Split client jar
    public static final class ClientOnly extends ZomboidJar {
        public ClientOnly(Path path) {
            super(path, false, true, false, Type.CLIENT_ONLY);
        }

        @Override
        public ZomboidJar forPath(Path path) {
            return new ClientOnly(path);
        }
    }
}

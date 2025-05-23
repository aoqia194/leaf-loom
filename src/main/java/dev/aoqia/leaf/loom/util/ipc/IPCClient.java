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
package dev.aoqia.leaf.loom.util.ipc;

import java.io.IOException;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import dev.aoqia.leaf.loom.util.IOStringConsumer;

public final class IPCClient implements IOStringConsumer, AutoCloseable {
    private final Path path;
    private final SocketChannel socketChannel;

    public IPCClient(Path path) throws IOException {
        this.path = path;
        socketChannel = setupChannel();
    }

    private SocketChannel setupChannel() throws IOException {
        final UnixDomainSocketAddress address = UnixDomainSocketAddress.of(path);
        return SocketChannel.open(address);
    }

    @Override
    public void accept(String s) throws IOException {
        synchronized (socketChannel) {
            ByteBuffer buf = ByteBuffer.wrap((s + "\n").getBytes(StandardCharsets.UTF_8));

            while (buf.hasRemaining()) {
                socketChannel.write(buf);
            }
        }
    }

    @Override
    public void close() throws Exception {
        synchronized (socketChannel) {
            socketChannel.close();
        }
    }
}

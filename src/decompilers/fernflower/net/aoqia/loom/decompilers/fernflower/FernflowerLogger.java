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
package net.aoqia.loom.decompilers.fernflower;

import java.io.IOException;
import net.aoqia.loom.decompilers.LoomInternalDecompiler;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;

public class FernflowerLogger extends IFernflowerLogger {
    private final LoomInternalDecompiler.Logger logger;

    public FernflowerLogger(LoomInternalDecompiler.Logger logger) {
        this.logger = logger;
    }

    @Override
    public void writeMessage(String message, Severity severity) {
        if (message.contains("Inconsistent inner class entries for")) return;
        if (message.contains("Inconsistent generic signature in method")) return;
        System.err.println(message);
    }

    @Override
    public void writeMessage(String message, Severity severity, Throwable t) {
        writeMessage(message, severity);
    }

    private void write(String data) {
        try {
            logger.accept(data);
        } catch (IOException e) {
            throw new RuntimeException("Failed to log", e);
        }
    }

    @Override
    public void startReadingClass(String className) {
        write("Decompiling " + className);
    }

    @Override
    public void startClass(String className) {
        write("Decompiling " + className);
    }

    @Override
    public void startWriteClass(String className) {
        // Nope
    }

    @Override
    public void startMethod(String methodName) {
        // Nope
    }

    @Override
    public void endMethod() {
        // Nope
    }
}

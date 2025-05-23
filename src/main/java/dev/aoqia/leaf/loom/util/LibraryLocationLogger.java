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

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import java.util.List;
import kotlin.metadata.jvm.KotlinClassMetadata;
import org.apache.commons.io.FileUtils;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.util.ASMifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * It's quite common for other plugins to shade these libraries, thus the wrong version is used.
 * This file logs out the version + location of each library as a debugging aid.
 *
 * <p>gradlew buildEnvironment is a useful command to run alongside this.
 */
public final class LibraryLocationLogger {
    private static final List<Class<?>> libraryClasses = List.of(
            KotlinClassMetadata.class,
            ClassVisitor.class,
            Analyzer.class,
            ClassRemapper.class,
            ClassNode.class,
            ASMifier.class,
            Gson.class,
            Preconditions.class,
            FileUtils.class);

    private static final Logger LOGGER = LoggerFactory.getLogger(LibraryLocationLogger.class);

    public static void logLibraryVersions() {
        for (Class<?> clazz : libraryClasses) {
            LOGGER.info(
                    "({}) with version ({}) was loaded from ({})",
                    clazz.getName(),
                    clazz.getPackage().getImplementationVersion(),
                    clazz.getProtectionDomain().getCodeSource().getLocation().getPath());
        }
    }

    private LibraryLocationLogger() {}
}

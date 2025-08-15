/*
 * This file is part of leaf-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2025 aoqia, FabricMC
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

import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

import dev.aoqia.leaf.loom.util.gradle.GradleTypeAdapter;

import com.google.common.base.Suppliers;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.tasks.Internal;

/**
 * A simple base class for creating cache keys. Extend this class and create abstract properties to
 * be included in the cache key.
 */
public abstract class CacheKey {
    private static final int CHECKSUM_LENGTH = 8;
    private final transient Supplier<String> jsonSupplier = Suppliers.memoize(
        () -> GradleTypeAdapter.GSON.toJson(this));
    private final transient Supplier<String> cacheKeySupplier = Suppliers.memoize(
        () -> Checksum.sha1Hex(jsonSupplier.get().getBytes(StandardCharsets.UTF_8))
            .substring(0, CHECKSUM_LENGTH));

    public static <T> T create(Project project, Class<T> clazz, Action<T> action) {
        T instance = project.getObjects().newInstance(clazz);
        action.execute(instance);
        return instance;
    }

    @Internal
    public final String getJson() {
        return jsonSupplier.get();
    }

    @Internal
    public final String getCacheKey() {
        return cacheKeySupplier.get();
    }
}

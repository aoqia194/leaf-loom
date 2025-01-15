/*
 * This file is part of leaf-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2023 aoqia, FabricMC
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
package net.aoqia.loom.configuration.accesswidener;

import java.io.IOException;
import net.fabricmc.accesswidener.AccessWidenerVisitor;
import net.aoqia.loom.util.LazyCloseable;
import net.aoqia.loom.util.fmj.ModEnvironment;
import net.fabricmc.tinyremapper.TinyRemapper;
import org.jetbrains.annotations.Nullable;

public interface AccessWidenerEntry {
    ModEnvironment environment();

    /**
     * @return The mod id to be used in {@link TransitiveAccessWidenerMappingsProcessor} or null when this entry does not contain transitive entries.
     */
    @Nullable
    String mappingId();

    String getSortKey();

    void read(AccessWidenerVisitor visitor, LazyCloseable<TinyRemapper> remapper) throws IOException;
}

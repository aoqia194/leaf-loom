/*
 * This file is part of leaf-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016-2021 aoqia, FabricMC
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
package dev.aoqia.leaf.loom.configuration.providers.zomboid.assets;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.annotations.SerializedName;

@SuppressWarnings("unused")
public record AssetIndex(
    Map<String, Entry> objects, boolean virtual, @SerializedName("map_to_resources") boolean mapToResources) {
    public AssetIndex() {
        this(new LinkedHashMap<>(), false, false);
    }

    public Collection<Object> getObjects() {
        return objects.entrySet().stream().map(Object::new).toList();
    }

    public record Entry(String size, String hash) {
    }

    public record Object(String path, String size, String hash) {
        private Object(Map.Entry<String, Entry> entry) {
            this(entry.getKey(),
                entry.getValue().size(),
                entry.getValue().hash());
        }

        public String name() {
            int end = path().lastIndexOf("/") + 1;

            if (end > 0) {
                return path().substring(end);
            }

            return path();
        }
    }
}

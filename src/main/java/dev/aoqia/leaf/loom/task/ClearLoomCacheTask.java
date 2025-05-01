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
package dev.aoqia.leaf.loom.task;

import java.io.IOException;

import dev.aoqia.leaf.loom.LoomGradleExtension;
import dev.aoqia.leaf.loom.util.Constants;

import org.apache.commons.io.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;

public abstract class ClearLoomCacheTask extends DefaultTask {
    public ClearLoomCacheTask() {
        setGroup(Constants.TaskGroup.LEAF);

        getCacheDir().set(getExtension().getFiles().getUserCache());
    }

    @InputFile
    public abstract RegularFileProperty getCacheDir();

    @Internal
    protected LoomGradleExtension getExtension() {
        return LoomGradleExtension.get(getProject());
    }

    @TaskAction
    public void run() {
        try {
            FileUtils.deleteDirectory(getCacheDir().get().getAsFile());
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete leaf-loom gradle cache directory.");
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(
                "Expected directory but found file when trying to delete leaf-loom gradle cache.");
        }
    }
}

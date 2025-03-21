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
package dev.aoqia.leaf.loom.task;

import java.io.File;
import javax.inject.Inject;
import dev.aoqia.leaf.loom.LoomGradleExtension;
import dev.aoqia.leaf.loom.util.Constants;
import org.gradle.api.tasks.Sync;

public abstract class ExtractNativesTask extends Sync {
    @Inject
    public ExtractNativesTask() {
        // Is there a lazy way to do this for many files? - Doesnt seem there is...
        for (File nativeFile : getProject()
                .getConfigurations()
                .getByName(Constants.Configurations.ZOMBOID_NATIVES)
                .getFiles()) {
            from(getProject().zipTree(nativeFile), copySpec -> {
                copySpec.exclude("META-INF/**");
                // Fix pre LWJGL 3 versions on Macos. See: https://github.com/FabricMC/fabric-loom/issues/955
                copySpec.rename(s -> s.replace(".jnilib", ".dylib"));
            });
        }

        into(LoomGradleExtension.get(getProject()).getFiles().getNativesDirectory(getProject()));

        setDescription("Extracts the zomboid natives");
    }
}

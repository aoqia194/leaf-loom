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
package dev.aoqia.leaf.loom.util.fmj;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import dev.aoqia.leaf.loom.LoomGradleExtension;
import dev.aoqia.leaf.loom.util.gradle.SourceSetHelper;
import org.gradle.api.Project;
import org.gradle.api.tasks.SourceSet;

public class LeafModJsonHelpers {
    // Returns a list of Mods found in the provided project's main or client sourcesets
    public static List<LeafModJson> getModsInProject(Project project) {
        final LoomGradleExtension extension = LoomGradleExtension.get(project);
        var sourceSets = new ArrayList<SourceSet>();
        sourceSets.add(SourceSetHelper.getMainSourceSet(project));

        if (extension.areEnvironmentSourceSetsSplit()) {
            sourceSets.add(SourceSetHelper.getSourceSetByName("client", project));
        }

        try {
            final LeafModJson leafModJson =
                    LeafModJsonFactory.createFromSourceSetsNullable(project, sourceSets.toArray(SourceSet[]::new));

            if (leafModJson != null) {
                return List.of(leafModJson);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return Collections.emptyList();
    }
}

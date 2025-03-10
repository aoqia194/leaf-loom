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
package dev.aoqia.leaf.loom.task.launch;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.inject.Inject;
import dev.aoqia.leaf.loom.task.AbstractLoomTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

public abstract class GenerateLog4jConfigTask extends AbstractLoomTask {
    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @Inject
    public GenerateLog4jConfigTask() {
        getOutputFile().set(getExtension().getFiles().getDefaultLog4jConfigFile());
    }

    @TaskAction
    public void run() {
        Path outputFile = getOutputFile().get().getAsFile().toPath();

        try (InputStream is = GenerateLog4jConfigTask.class.getClassLoader().getResourceAsStream("log4j2.leaf.xml")) {
            Files.deleteIfExists(outputFile);
            Files.copy(is, outputFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate log4j config", e);
        }
    }
}

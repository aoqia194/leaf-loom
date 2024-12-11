/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021 FabricMC
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

package net.aoqia.loom.task.launch;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import net.aoqia.loom.LoomGradleExtension;
import net.aoqia.loom.LoomGradlePlugin;
import net.aoqia.loom.configuration.providers.zomboid.ZomboidVersionMeta;
import net.aoqia.loom.configuration.providers.zomboid.mapped.MappedZomboidProvider;
import net.aoqia.loom.task.AbstractLoomTask;
import net.aoqia.loom.util.gradle.SourceSetHelper;
import org.apache.commons.io.FileUtils;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.configuration.ConsoleOutput;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.*;

public abstract class GenerateDLIConfigTask extends AbstractLoomTask {
    public GenerateDLIConfigTask() {
        getVersionInfoJson()
            .set(LoomGradlePlugin.GSON.toJson(
                getExtension().getZomboidProvider().getClientVersionInfo()));
        getZomboidVersion().set(getExtension().getZomboidProvider().clientZomboidVersion());
        getSplitSourceSets().set(getExtension().areEnvironmentSourceSetsSplit());
        getANSISupportedIDE().set(ansiSupportedIde(getProject()));
        getPlainConsole().set(getProject().getGradle().getStartParameter().getConsoleOutput() == ConsoleOutput.Plain);

        if (!getExtension().getMods().isEmpty()) {
            getClassPathGroups().set(buildClassPathGroups(getProject()));
        }

        getLog4jConfigPaths().set(getAllLog4JConfigFiles(getProject()));

        if (getSplitSourceSets().get()) {
            getClientGameJarPath().set(getGameJarPath("client"));
            getCommonGameJarPath().set(getGameJarPath("common"));
        }

        getClientInstallPath().set(new File(getExtension().getFiles().getUserCache(), "assets").getAbsolutePath());
        getNativesDirectoryPath()
            .set(getExtension().getFiles().getNativesDirectory(getProject()).getAbsolutePath());
        getDevLauncherConfig().set(getExtension().getFiles().getDevLauncherConfig());
    }

    @Input
    protected abstract Property<String> getVersionInfoJson();

    @Input
    protected abstract Property<String> getZomboidVersion();

    @Input
    protected abstract Property<Boolean> getSplitSourceSets();

    @Input
    protected abstract Property<Boolean> getPlainConsole();

    @Input
    protected abstract Property<Boolean> getANSISupportedIDE();

    @Input
    @Optional
    protected abstract Property<String> getClassPathGroups();

    @Input
    protected abstract Property<String> getLog4jConfigPaths();

    @Input
    @Optional
    protected abstract Property<String> getClientGameJarPath();

    @Input
    @Optional
    protected abstract Property<String> getCommonGameJarPath();

    @Input
    protected abstract Property<String> getClientInstallPath();

    @Input
    protected abstract Property<String> getNativesDirectoryPath();

    @OutputFile
    protected abstract RegularFileProperty getDevLauncherConfig();

    private static String getAllLog4JConfigFiles(Project project) {
        return LoomGradleExtension.get(project).getLog4jConfigs().getFiles().stream()
            .map(File::getAbsolutePath)
            .collect(Collectors.joining(","));
    }

    private String getGameJarPath(String env) {
        MappedZomboidProvider.Split split =
            (MappedZomboidProvider.Split) getExtension().getNamedZomboidProvider();

        return switch (env) {
            case "client" -> split.getClientOnlyJar().getPath().toAbsolutePath().toString();
            case "common" -> split.getCommonJar().getPath().toAbsolutePath().toString();
            default -> throw new UnsupportedOperationException();
        };
    }

    /**
     * See: https://github.com/FabricMC/fabric-loader/pull/585.
     */
    private static String buildClassPathGroups(Project project) {
        return LoomGradleExtension.get(project).getMods().stream()
            .map(modSettings -> SourceSetHelper.getClasspath(modSettings, project).stream()
                .map(File::getAbsolutePath)
                .collect(Collectors.joining(File.pathSeparator)))
            .collect(Collectors.joining(File.pathSeparator + File.pathSeparator));
    }

    private static boolean ansiSupportedIde(Project project) {
        File rootDir = project.getRootDir();
        return new File(rootDir, ".vscode").exists()
               || new File(rootDir, ".idea").exists()
               || new File(rootDir, ".project").exists()
               || (Arrays.stream(rootDir.listFiles())
            .anyMatch(file -> file.getName().endsWith(".iws")));
    }

    @Input
    protected abstract Property<String> getServerInstallPath();

    @TaskAction
    public void run() throws IOException {
        final ZomboidVersionMeta versionInfo =
            LoomGradlePlugin.GSON.fromJson(getVersionInfoJson().get(), ZomboidVersionMeta.class);

        final LaunchConfig launchConfig = new LaunchConfig()
            .property("leaf.development", "true")
            .property(
                "leaf.remapClasspathFile",
                getRemapClasspathFile().get().getAsFile().getAbsolutePath())
            .property("log4j.configurationFile", getLog4jConfigPaths().get())
            .property("log4j2.formatMsgNoLookups", "true");

        if (versionInfo.hasNativesToExtract()) {
            String nativesPath = getNativesDirectoryPath().get();

            launchConfig
                .property("client", "java.library.path", nativesPath)
                .property("client", "org.lwjgl.librarypath", nativesPath);
        }

        if (getSplitSourceSets().get()) {
            launchConfig.property(
                "client",
                "leaf.gameJarPath.client",
                getClientGameJarPath().get());
            launchConfig.property("leaf.gameJarPath", getCommonGameJarPath().get());
        }

        if (getClassPathGroups().isPresent()) {
            launchConfig.property("leaf.classPathGroups", getClassPathGroups().get());
        }

        // Enable ansi by default for idea and vscode when gradle is not ran with plain console.
        if (getANSISupportedIDE().get() && !getPlainConsole().get()) {
            launchConfig.property("leaf.log.disableAnsi", "false");
        }

        FileUtils.writeStringToFile(
            getDevLauncherConfig().getAsFile().get(), launchConfig.asString(), StandardCharsets.UTF_8);
    }

    @InputFile
    public abstract RegularFileProperty getRemapClasspathFile();

    public static class LaunchConfig {
        private final Map<String, List<String>> values = new HashMap<>();

        public LaunchConfig property(String key, String value) {
            return property("common", key, value);
        }

        public LaunchConfig property(String side, String key, String value) {
            values.computeIfAbsent(side + "Properties", (s -> new ArrayList<>()))
                .add(String.format("%s=%s", key, value));
            return this;
        }

        public LaunchConfig argument(String value) {
            return argument("common", value);
        }

        public LaunchConfig argument(String side, String value) {
            values.computeIfAbsent(side + "Args", (s -> new ArrayList<>())).add(value);
            return this;
        }

        public String asString() {
            StringJoiner stringJoiner = new StringJoiner("\n");

            for (Map.Entry<String, List<String>> entry : values.entrySet()) {
                stringJoiner.add(entry.getKey());

                for (String s : entry.getValue()) {
                    stringJoiner.add("\t" + s);
                }
            }

            return stringJoiner.toString();
        }
    }
}

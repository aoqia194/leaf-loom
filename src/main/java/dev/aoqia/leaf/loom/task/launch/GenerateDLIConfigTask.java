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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import dev.aoqia.leaf.loom.LoomGradleExtension;
import dev.aoqia.leaf.loom.LoomGradlePlugin;
import dev.aoqia.leaf.loom.configuration.providers.zomboid.ZomboidVersionMeta;
import dev.aoqia.leaf.loom.configuration.providers.zomboid.mapped.MappedZomboidProvider;
import dev.aoqia.leaf.loom.task.AbstractLoomTask;
import dev.aoqia.leaf.loom.util.Platform;
import dev.aoqia.leaf.loom.util.gradle.SourceSetHelper;
import org.apache.commons.io.FileUtils;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.configuration.ConsoleOutput;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;
import org.gradle.api.tasks.Optional;

public abstract class GenerateDLIConfigTask extends AbstractLoomTask {
    public GenerateDLIConfigTask() {
        getVersionInfoJson()
            .set(LoomGradlePlugin.GSON.toJson(
                getExtension().getZomboidProvider().getClientVersionInfo()));
        getZomboidVersion().set(
            getExtension().getZomboidProvider().clientZomboidVersion());
        getSplitSourceSets().set(getExtension().areEnvironmentSourceSetsSplit());
        getANSISupportedIDE().set(ansiSupportedIde(getProject()));
        getPlainConsole().set(
            getProject().getGradle().getStartParameter().getConsoleOutput() ==
            ConsoleOutput.Plain);

        if (!getExtension().getMods().isEmpty()) {
            getClassPathGroups().set(buildClassPathGroups(getProject()));
        }

        getLog4jConfigPaths().set(getAllLog4JConfigFiles(getProject()));

        if (getSplitSourceSets().get()) {
            getClientGameJarPath().set(getGameJarPath("client"));
            getCommonGameJarPath().set(getGameJarPath("common"));
        }

        getNativesDirectoryPath().set(getExtension().getFiles()
            .getNativesDirectory(getProject())
            .getAbsolutePath());
        getDevLauncherConfig().set(getExtension().getFiles().getDevLauncherConfig());
    }

    private static boolean ansiSupportedIde(Project project) {
        File rootDir = project.getRootDir();
        return new File(rootDir, ".vscode").exists()
               || new File(rootDir, ".idea").exists()
               || new File(rootDir, ".project").exists()
               || (Arrays.stream(rootDir.listFiles())
            .anyMatch(file -> file.getName().endsWith(".iws")));
    }

    /**
     * See: https://github.com/FabricMC/fabric-loader/pull/585.
     */
    private static String buildClassPathGroups(Project project) {
        return LoomGradleExtension.get(project).getMods().stream()
            .map(
                modSettings -> SourceSetHelper.getClasspath(modSettings, project).stream()
                    .map(File::getAbsolutePath)
                    .collect(Collectors.joining(File.pathSeparator)))
            .collect(Collectors.joining(File.pathSeparator + File.pathSeparator));
    }

    private static String getAllLog4JConfigFiles(Project project) {
        return LoomGradleExtension.get(project).getLog4jConfigs().getFiles().stream()
            .map(File::getAbsolutePath)
            .collect(Collectors.joining(","));
    }

    @Input
    protected abstract Property<String> getVersionInfoJson();

    @Input
    protected abstract Property<String> getZomboidVersion();

    @Input
    protected abstract Property<Boolean> getSplitSourceSets();

    @Input
    protected abstract Property<Boolean> getANSISupportedIDE();

    @Input
    protected abstract Property<Boolean> getPlainConsole();

    @Input
    @Optional
    protected abstract Property<String> getClassPathGroups();

    @Input
    protected abstract Property<String> getLog4jConfigPaths();

    @Input
    @Optional
    protected abstract Property<String> getClientGameJarPath();

    private String getGameJarPath(String env) {
        MappedZomboidProvider.Split split =
            (MappedZomboidProvider.Split) getExtension().getNamedZomboidProvider();

        return switch (env) {
            case "client" -> split.getClientOnlyJar().getPath().toAbsolutePath().toString();
            case "common" -> split.getCommonJar().getPath().toAbsolutePath().toString();
            default -> throw new UnsupportedOperationException();
        };
    }

    @Input
    @Optional
    protected abstract Property<String> getCommonGameJarPath();

    @Input
    protected abstract Property<String> getNativesDirectoryPath();

    @OutputFile
    protected abstract RegularFileProperty getDevLauncherConfig();

    @TaskAction
    public void run() throws IOException {
        final ZomboidVersionMeta versionInfo =
            LoomGradlePlugin.GSON.fromJson(getVersionInfoJson().get(),
                ZomboidVersionMeta.class);

        final LaunchConfig launchConfig = new LaunchConfig()
            .property("leaf.development", "true")
            .property(
                "leaf.remapClasspathFile",
                getRemapClasspathFile().get().getAsFile().getAbsolutePath())
            .property("log4j.configurationFile", getLog4jConfigPaths().get())
            .property("log4j2.formatMsgNoLookups", "true");

        // I don't add game args from the versionInfo manifest because I don't need them rn.
        // Add the jvm args from the versionInfo manifest.
        for (final var arg : versionInfo.arguments().jvm()) {
            if (arg.isJsonPrimitive()) {
                String str = arg.getAsJsonPrimitive().getAsString();
                String key = str.subSequence(2, str.indexOf("=")).toString();
                String value = str.subSequence(str.indexOf("=") + 1, str.length()).toString();

                // Skip this because we set it in the runconfig's jvm args.
                if (key.startsWith("java.library.path")) {
                    continue;
                }

                launchConfig.property(key, value);
            } else if (arg.isJsonObject()) {
                final var argument = LoomGradlePlugin.GSON.fromJson(arg,
                    ZomboidVersionMeta.Argument.class);
                final var argumentRules = argument.rules();
                final var argumentValue = argument.value();

                boolean allowed = false;
                for (final var rule : argumentRules) {
                    if (rule.isAllowed() && rule.appliesToOS(Platform.CURRENT)) {
                        allowed = true;
                    }
                }

                if (!allowed) {
                    continue;
                }

                HashMap<String, String> props = new HashMap<>();
                if (argumentValue.isJsonPrimitive()) {
                    final String str = argumentValue.getAsJsonPrimitive().getAsString();
                    final String key = str.subSequence(2, str.indexOf("=")).toString();
                    final String value = str.subSequence(str.indexOf("=") + 1, str.length())
                        .toString();
                    props.put(key, value);
                } else if (argumentValue.isJsonArray()) {
                    final var arguments = argumentValue.getAsJsonArray();
                    for (final var e : arguments) {
                        final String str = e.getAsString();
                        final String key = str.subSequence(2, str.indexOf("=")).toString();
                        final String value = str.subSequence(str.indexOf("=") + 1, str.length())
                            .toString();
                        props.put(key, value);
                    }
                } else {
                    throw new IllegalStateException(
                        "values in ZomboidVersionMeta json wasn't a string or list of strings.");
                }

                // Skip this because we set it in the runconfig's jvm args.
                props.remove("java.library.path");
                props.forEach(launchConfig::property);
            } else {
                throw new IllegalStateException(
                    "arg in ZomboidVersionMeta json wasn't a string or object.");
            }
        }

        if (versionInfo.hasNativesToExtract()) {
            throw new RuntimeException(
                "GenerateDLIConfigTask.hasNativesToExtract() -> We shouldn't be here! " +
                "Report me to a developer pls >w<");
            //            String nativesPath = getNativesDirectoryPath().get();
            //
            //            launchConfig
            //                .property("client", "java.library.path", nativesPath)
            //                .property("client", "org.lwjgl.librarypath", nativesPath);
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

        // Enable ansi by default for idea and vscode when gradle is not ran with plain
        // console.
        if (getANSISupportedIDE().get() && !getPlainConsole().get()) {
            launchConfig.property("leaf.log.disableAnsi", "false");
        }

        FileUtils.writeStringToFile(getDevLauncherConfig().getAsFile().get(),
            launchConfig.asString(), StandardCharsets.UTF_8);
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

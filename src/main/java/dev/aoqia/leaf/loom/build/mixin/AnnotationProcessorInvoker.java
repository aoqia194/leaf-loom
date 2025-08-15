/*
 * This file is part of leaf-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2020-2022 aoqia, FabricMC
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
package dev.aoqia.leaf.loom.build.mixin;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import dev.aoqia.leaf.loom.LoomGradleExtension;
import dev.aoqia.leaf.loom.configuration.ide.idea.IdeaUtils;
import dev.aoqia.leaf.loom.configuration.providers.zomboid.ZomboidSourceSets;
import dev.aoqia.leaf.loom.extension.MixinExtension;
import dev.aoqia.leaf.loom.util.Constants;
import dev.aoqia.leaf.loom.util.LoomVersions;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;

/**
 * Normally javac invokes annotation processors, but when the scala or kapt plugin are installed they will want to
 * invoke the annotation processor themselves. See Java and Kapt implementations for a more deep understanding of the
 * things passed by the children.
 */
public abstract class AnnotationProcessorInvoker<T extends Task> {
    public static final String JAVA = "java";
    public static final String SCALA = "scala";
    public static final String GROOVY = "groovy";

    private static final Pattern MSG_KEY_PATTERN = Pattern.compile("^[A-Z]+[A-Z_]+$");
    private static final Pattern MSG_VALUE_PATTERN = Pattern.compile("^(note|warning|error|disabled)$");

    protected final Project project;
    protected final MixinExtension mixinExtension;
    protected final Map<SourceSet, TaskProvider<T>> invokerTasks;
    private final LoomGradleExtension loomExtension;
    private final String name;
    private final Collection<Configuration> apConfigurations;

    protected AnnotationProcessorInvoker(
        Project project, Collection<Configuration> apConfigurations, Map<SourceSet, TaskProvider<T>> invokerTasks, String name) {
        this.project = project;
        this.loomExtension = LoomGradleExtension.get(project);
        this.mixinExtension = loomExtension.getMixin();
        this.apConfigurations = apConfigurations;
        this.invokerTasks = invokerTasks;
        this.name = name;
    }

    protected static Collection<Configuration> getApConfigurations(
        Project project, Function<SourceSet, String> getApConfigNameFunc) {
        MixinExtension mixin = LoomGradleExtension.get(project).getMixin();
        return mixin.getApConfigurationsStream(getApConfigNameFunc).collect(Collectors.toList());
    }

    public void  configureMixin() {
        ConfigurationContainer configs = project.getConfigurations();
        ZomboidSourceSets zomboidSourceSets = ZomboidSourceSets.get(project);

        if (!IdeaUtils.isIdeaSync()) {
            for (Configuration processorConfig : apConfigurations) {
                project.getLogger().info("Adding mixin to classpath of AP config: {}", processorConfig.getName());
                // Pass named MC classpath to mixin AP classpath
                processorConfig.extendsFrom(
                    configs.getByName(Constants.Configurations.LOADER_DEPENDENCIES),
                    configs.getByName(Constants.Configurations.MAPPINGS_FINAL));

                // Add Mixin and mixin extensions (fabric-mixin-compile-extensions pulls mixin itself too)
                project.getDependencies().add(processorConfig.getName(), LoomVersions.SPONGE_MIXIN.mavenNotation());
                //                project.getDependencies()
                //                        .add(processorConfig.getName(), LoomVersions.MIXIN_COMPILE_EXTENSIONS
                //                        .mavenNotation());
            }
        }

        for (Map.Entry<SourceSet, TaskProvider<T>> entry : invokerTasks.entrySet()) {
            entry.getValue().configure(t -> passMixinArguments(t, entry.getKey()));
        }
    }

    private void passMixinArguments(T task, SourceSet sourceSet) {
        try {
            LoomGradleExtension loom = LoomGradleExtension.get(project);
            String refmapName = Objects.requireNonNull(MixinExtension.getMixinInformationContainer(sourceSet))
                .refmapNameProvider()
                .get();

            final File mixinMappings = getMixinMappingsForSourceSet(project, sourceSet);

            task.getOutputs()
                .file(mixinMappings)
                .withPropertyName("mixin-ap-" + sourceSet.getName() + "-" + name)
                .optional();

            Map<String, String> args = new HashMap<>() {
                {
                    put(
                        Constants.MixinArguments.IN_MAP_FILE_NAMED_INTERMEDIARY,
                        loom.getMappingConfiguration().tinyMappings.toFile().getCanonicalPath());
                    put(Constants.MixinArguments.OUT_MAP_FILE_NAMED_INTERMEDIARY, mixinMappings.getCanonicalPath());
                    put(Constants.MixinArguments.OUT_REFMAP_FILE, getRefmapDestination(task, refmapName));
                    put(
                        Constants.MixinArguments.DEFAULT_OBFUSCATION_ENV,
                        "named:"
                        + loom.getMixin().getRefmapTargetNamespace().get());
                    put(Constants.MixinArguments.QUIET, "true");
                }
            };

            if (mixinExtension.getShowMessageTypes().get()) {
                args.put(Constants.MixinArguments.SHOW_MESSAGE_TYPES, "true");
            }

            mixinExtension.getMessages().get().forEach((key, value) -> {
                checkPattern(key, MSG_KEY_PATTERN);
                checkPattern(value, MSG_VALUE_PATTERN);

                args.put("MSG_" + key, value);
            });

            project.getLogger()
                .debug("Outputting refmap to dir: " + getRefmapDestinationDir(task) + " for compile task: " + task);
            args.forEach((k, v) -> passArgument(task, k, v));
        } catch (IOException e) {
            project.getLogger().error("Could not configure mixin annotation processors", e);
        }
    }

    public static File getMixinMappingsForSourceSet(Project project, SourceSet sourceSet) {
        final LoomGradleExtension extension = LoomGradleExtension.get(project);
        return new File(
            extension.getFiles().getProjectBuildCache(),
            "mixin-map-" + extension.getMappingConfiguration().mappingsIdentifier() + "." + sourceSet.getName()
            + ".tiny");
    }

    protected final String getRefmapDestination(T task, String refmapName) throws IOException {
        return new File(getRefmapDestinationDir(task), refmapName).getCanonicalPath();
    }

    private static void checkPattern(String input, Pattern pattern) {
        final Matcher matcher = pattern.matcher(input);

        if (!matcher.find()) {
            throw new IllegalArgumentException(
                "Mixin argument (%s) does not match pattern (%s)".formatted(input, pattern.toString()));
        }
    }

    protected abstract File getRefmapDestinationDir(T task);

    protected abstract void passArgument(T compileTask, String key, String value);
}

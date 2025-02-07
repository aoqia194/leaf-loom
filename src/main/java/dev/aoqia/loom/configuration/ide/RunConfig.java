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
package dev.aoqia.loom.configuration.ide;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.aoqia.loom.LoomGradleExtension;
import dev.aoqia.loom.configuration.InstallerData;
import dev.aoqia.loom.configuration.ide.idea.IdeaSyncTask;
import dev.aoqia.loom.configuration.ide.idea.IdeaUtils;
import dev.aoqia.loom.configuration.providers.zomboid.library.LibraryContext;
import dev.aoqia.loom.util.gradle.SourceSetReference;
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedModuleVersion;
import org.gradle.api.tasks.SourceSet;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class RunConfig {
    public String configName;
    public String eclipseProjectName;
    public String ideaModuleName;
    public String mainClass;
    public String runDirIdeaUrl;
    public String workingDir;
    public String environment;
    public String runDir;
    public List<String> vmArgs = new ArrayList<>();
    public List<String> programArgs = new ArrayList<>();
    public transient SourceSet sourceSet;
    public Map<String, Object> environmentVariables;
    public String projectName;

    // Turns camelCase/PascalCase into Capital Case
    // caseConversionExample -> Case Conversion Example
    private static String capitalizeCamelCaseName(String name) {
        if (name.length() == 0) {
            return "";
        }

        return name.substring(0, 1).toUpperCase() + name.substring(1).replaceAll("([^A-Z])([A-Z])", "$1 $2");
    }

    public static RunConfig runConfig(Project project, RunConfigSettings settings) {
        LoomGradleExtension extension = LoomGradleExtension.get(project);
        LibraryContext context =
            new LibraryContext(extension.getZomboidProvider().getClientVersionInfo(), JavaVersion.current());

        if (settings.getEnvironment().equals("client") && context.usesLWJGL3()) {
            settings.startFirstThread();
        }

        String name = settings.getName();

        String configName = settings.getConfigName();
        String environment = settings.getEnvironment();
        SourceSet sourceSet = settings.getSource(project);

        String mainClass = settings.getMainClass().getOrNull();

        if (mainClass == null) {
            throw new IllegalArgumentException("Run configuration '" + name + "' must specify 'mainClass'");
        }

        if (configName == null) {
            configName = "";
            String srcName = sourceSet.getName();

            final boolean isSplitClientSourceSet = extension.areEnvironmentSourceSetsSplit()
                                                   && srcName.equals("client")
                                                   && environment.equals("client");

            if (!srcName.equals(SourceSet.MAIN_SOURCE_SET_NAME) && !isSplitClientSourceSet) {
                configName += capitalizeCamelCaseName(srcName) + " ";
            }

            configName += "Zomboid " + capitalizeCamelCaseName(name);
        }

        Objects.requireNonNull(environment, "No environment set for run config");

        String workingDir = extension.getZomboidProvider().extractedDir().toString();
        String runDir = settings.getRunDir();

        boolean appendProjectPath = settings.getAppendProjectPathToConfigName().get();
        RunConfig runConfig = new RunConfig();
        runConfig.configName = configName;

        if (appendProjectPath && !extension.isRootProject()) {
            runConfig.configName += " (" + project.getPath() + ")";
        }

        runConfig.mainClass = settings.devLaunchMainClass().get();
        runConfig.vmArgs.add("-Dfabric.dli.config="
                             + encodeEscaped(extension.getFiles().getDevLauncherConfig().getAbsolutePath()));
        runConfig.vmArgs.add("-Dfabric.dli.env=" + environment.toLowerCase());

        // Need this here so the loader can find the project's rundir.
        runConfig.vmArgs.add("-Dleaf.runDir=" + runDir);
        // To prevent the log file from creating in workingdir.
        runConfig.vmArgs.add("-Dleaf.log.file=" + Path.of(runDir).resolve("leafloader.log"));

        runConfig.eclipseProjectName = project.getExtensions()
            .getByType(EclipseModel.class)
            .getProject()
            .getName();
        runConfig.ideaModuleName = IdeaUtils.getIdeaModuleName(new SourceSetReference(sourceSet, project));
        runConfig.runDirIdeaUrl = "file://" + workingDir;
        runConfig.workingDir = workingDir;
        runConfig.runDir = runDir;
        runConfig.sourceSet = sourceSet;
        runConfig.environment = environment;

        // Custom parameters
        runConfig.programArgs.addAll(settings.getProgramArgs());
        runConfig.vmArgs.addAll(settings.getVmArgs());
        runConfig.vmArgs.add("-Dfabric.dli.main=" + mainClass);
        runConfig.environmentVariables = new HashMap<>();
        runConfig.environmentVariables.putAll(settings.getEnvironmentVariables());
        runConfig.projectName = project.getName();

        return runConfig;
    }

    public static String joinArguments(List<String> args) {
        final var sb = new StringBuilder();
        boolean first = true;

        for (String arg : args) {
            if (!first) {
                sb.append(" ");
            }

            first = false;

            if (arg.contains(" ")) {
                sb.append("\"").append(arg).append("\"");
            } else {
                sb.append(arg);
            }
        }

        return sb.toString();
    }

    static String getMainClass(String side, LoomGradleExtension extension, String defaultMainClass) {
        InstallerData installerData = extension.getInstallerData();

        if (installerData == null) {
            return defaultMainClass;
        }

        JsonObject installerJson = installerData.installerJson();

        if (installerJson != null && installerJson.has("mainClass")) {
            JsonElement mainClassJson = installerJson.get("mainClass");

            String mainClassName = "";

            if (mainClassJson.isJsonObject()) {
                JsonObject mainClassesJson = mainClassJson.getAsJsonObject();

                if (mainClassesJson.has(side)) {
                    mainClassName = mainClassesJson.get(side).getAsString();
                }
            } else {
                mainClassName = mainClassJson.getAsString();
            }

            return mainClassName;
        }

        return defaultMainClass;
    }

    private static Set<ResolvedArtifact> getArtifacts(Project project, String configuration) {
        return project.getConfigurations().getByName(configuration).getHierarchy().stream()
            .map(c -> c.getResolvedConfiguration().getResolvedArtifacts())
            .flatMap(Collection::stream)
            .collect(Collectors.toSet());
    }

    private static boolean containsLibrary(Set<ResolvedArtifact> artifacts, ModuleVersionIdentifier identifier) {
        return artifacts.stream()
            .map(ResolvedArtifact::getModuleVersion)
            .map(ResolvedModuleVersion::getId)
            .anyMatch(test -> test.getGroup().equals(identifier.getGroup())
                              && test.getName().equals(identifier.getName()));
    }

    private static String encodeEscaped(String s) {
        StringBuilder ret = new StringBuilder();

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);

            if (c == '@' && i > 0 && s.charAt(i - 1) == '@' || c == ' ') {
                ret.append(String.format(Locale.ENGLISH, "@@%04x", (int) c));
            } else {
                ret.append(c);
            }
        }

        return ret.toString();
    }

    public Element genRuns(Element doc) {
        Element root = this.addXml(doc, "component", ImmutableMap.of("name", "ProjectRunConfigurationManager"));
        root = addXml(
            root,
            "configuration",
            ImmutableMap.of(
                "default", "false", "name", configName, "type", "Application", "factoryName", "Application"));

        this.addXml(root, "module", ImmutableMap.of("name", ideaModuleName));
        this.addXml(root, "option", ImmutableMap.of("name", "MAIN_CLASS_NAME", "value", mainClass));
        this.addXml(root, "option", ImmutableMap.of("name", "WORKING_DIRECTORY", "value", runDirIdeaUrl));

        if (!vmArgs.isEmpty()) {
            this.addXml(root, "option", ImmutableMap.of("name", "VM_PARAMETERS", "value", joinArguments(vmArgs)));
        }

        if (!programArgs.isEmpty()) {
            this.addXml(
                root, "option", ImmutableMap.of("name", "PROGRAM_PARAMETERS", "value", joinArguments(programArgs)));
        }

        return root;
    }

    public Element addXml(Node parent, String name, Map<String, String> values) {
        Document doc = parent.getOwnerDocument();

        if (doc == null) {
            doc = (Document) parent;
        }

        Element e = doc.createElement(name);

        for (Map.Entry<String, String> entry : values.entrySet()) {
            e.setAttribute(entry.getKey(), entry.getValue());
        }

        parent.appendChild(e);
        return e;
    }

    public String fromDummy(String dummy, boolean relativeDir, Project project) throws IOException {
        String dummyConfig;

        try (InputStream input = IdeaSyncTask.class.getClassLoader().getResourceAsStream(dummy)) {
            dummyConfig = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        String runDir = this.workingDir;

        if (relativeDir && project.getRootProject() != project) {
            Path rootPath = project.getRootDir().toPath();
            Path projectPath = project.getProjectDir().toPath();
            String relativePath = rootPath.relativize(projectPath).toString();

            runDir = relativePath + "/" + runDir;
        }

        dummyConfig = dummyConfig.replace("%NAME%", configName);
        dummyConfig = dummyConfig.replace("%MAIN_CLASS%", mainClass);
        dummyConfig = dummyConfig.replace("%ECLIPSE_PROJECT%", eclipseProjectName);
        dummyConfig = dummyConfig.replace("%IDEA_MODULE%", ideaModuleName);
        dummyConfig = dummyConfig.replace("%RUN_DIRECTORY%", runDir);
        dummyConfig = dummyConfig.replace("%PROGRAM_ARGS%", joinArguments(programArgs).replaceAll("\"", "&quot;"));
        dummyConfig = dummyConfig.replace("%VM_ARGS%", joinArguments(vmArgs).replaceAll("\"", "&quot;"));
        dummyConfig = dummyConfig.replace("%IDEA_ENV_VARS%", getEnvVars("<env name=\"%s\" value=\"%s\"/>"));
        dummyConfig = dummyConfig.replace("%ECLIPSE_ENV_VARS%", getEnvVars("<mapEntry key=\"%s\" value=\"%s\"/>"));

        return dummyConfig;
    }

    private String getEnvVars(String pattern) {
        return environmentVariables.entrySet().stream()
            .map(entry -> pattern.formatted(entry.getKey(), entry.getValue().toString()))
            .collect(Collectors.joining());
    }
}

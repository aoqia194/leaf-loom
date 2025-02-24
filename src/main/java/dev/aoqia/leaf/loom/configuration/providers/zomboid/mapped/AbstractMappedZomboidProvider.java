/*
 * This file is part of leaf-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021-2022 aoqia, FabricMC
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
package dev.aoqia.leaf.loom.configuration.providers.zomboid.mapped;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.function.Function;

import dev.aoqia.leaf.loom.LoomGradleExtension;
import dev.aoqia.leaf.loom.api.mappings.layered.MappingsNamespace;
import dev.aoqia.leaf.loom.configuration.ConfigContext;
import dev.aoqia.leaf.loom.configuration.mods.dependency.LocalMavenHelper;
import dev.aoqia.leaf.loom.configuration.providers.mappings.MappingConfiguration;
import dev.aoqia.leaf.loom.configuration.providers.zomboid.SignatureFixerApplyVisitor;
import dev.aoqia.leaf.loom.configuration.providers.zomboid.ZomboidJar;
import dev.aoqia.leaf.loom.configuration.providers.zomboid.ZomboidProvider;
import dev.aoqia.leaf.loom.configuration.providers.zomboid.ZomboidSourceSets;
import dev.aoqia.leaf.loom.extension.LoomFiles;
import dev.aoqia.leaf.loom.util.SidedClassVisitor;
import dev.aoqia.leaf.loom.util.TinyRemapperHelper;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;
import org.gradle.api.Project;

public abstract class AbstractMappedZomboidProvider<M extends ZomboidProvider>
    implements MappedZomboidProvider.ProviderImpl {
    protected final M zomboidProvider;
    protected final LoomGradleExtension extension;
    private final Project project;

    public AbstractMappedZomboidProvider(Project project, M zomboidProvider) {
        this.zomboidProvider = zomboidProvider;
        this.project = project;
        this.extension = LoomGradleExtension.get(project);
    }

    // Create two copies of the remapped jar, the backup jar is used as the input of genSources
    public static Path getBackupJarPath(ZomboidJar zomboidJar) {
        final Path outputJarPath = zomboidJar.getPath();
        return outputJarPath.resolveSibling(outputJarPath.getFileName() + ".backup");
    }

    // Configure the remapper to add the client @Environment annotation to all classes in the client jar.
    public static void configureSplitRemapper(RemappedJars remappedJars, TinyRemapper.Builder tinyRemapperBuilder) {
        final ZomboidJar outputJar = remappedJars.outputJar();
        assert !outputJar.isMerged();

        if (outputJar.includesClient()) {
            assert !outputJar.includesServer();
            tinyRemapperBuilder.extraPostApplyVisitor(SidedClassVisitor.CLIENT);
        }
    }

    public abstract List<RemappedJars> getRemappedJars();

    // Returns a list of MinecraftJar.Type's that this provider exports to be used as a dependency
    public List<ZomboidJar.Type> getDependencyTypes() {
        return Collections.emptyList();
    }

    public List<ZomboidJar> provide(ProvideContext context) throws Exception {
        final List<RemappedJars> remappedJars = getRemappedJars();
        final List<ZomboidJar> zomboidJars =
            remappedJars.stream().map(RemappedJars::outputJar).toList();

        if (remappedJars.isEmpty()) {
            throw new IllegalStateException("No remapped jars provided");
        }

        if (!areOutputsValid(remappedJars) || context.refreshOutputs() || !hasBackupJars(zomboidJars)) {
            try {
                remapInputs(remappedJars, context.configContext());
                createBackupJars(zomboidJars);
            } catch (Throwable t) {
                cleanOutputs(remappedJars);

                throw new RuntimeException("Failed to remap zomboid", t);
            }
        }

        if (context.applyDependencies()) {
            final List<ZomboidJar.Type> dependencyTargets = getDependencyTypes();

            if (!dependencyTargets.isEmpty()) {
                ZomboidSourceSets.get(getProject())
                    .applyDependencies(
                        (configuration, type) ->
                            getProject().getDependencies().add(configuration, getDependencyNotation(type)),
                        dependencyTargets);
            }
        }

        return zomboidJars;
    }

    protected boolean hasBackupJars(List<ZomboidJar> zomboidJars) {
        for (ZomboidJar zomboidJar : zomboidJars) {
            if (!Files.exists(getBackupJarPath(zomboidJar))) {
                return false;
            }
        }

        return true;
    }

    protected void createBackupJars(List<ZomboidJar> zomboidJars) throws IOException {
        for (ZomboidJar zomboidJar : zomboidJars) {
            Files.copy(zomboidJar.getPath(), getBackupJarPath(zomboidJar), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    public Path getJar(ZomboidJar.Type type) {
        return getMavenHelper(type).getOutputFile(null);
    }

    public LocalMavenHelper getMavenHelper(ZomboidJar.Type type) {
        return new LocalMavenHelper(
            "com.theindiestone",
            getName(type),
            getVersion(),
            null,
            getMavenScope().getRoot(extension));
    }

    public abstract MavenScope getMavenScope();

    protected String getName(ZomboidJar.Type type) {
        var sj = new StringJoiner("-");
        sj.add("zomboid");
        sj.add(type.toString());

        // Include the intermediate mapping name if it's not the default intermediary
        //        if (!intermediateName.equals(IntermediaryMappingsProvider.NAME)) {
        //            sj.add(intermediateName);
        //        }

        if (getTargetNamespace() != MappingsNamespace.NAMED) {
            sj.add(getTargetNamespace().name());
        }

        return sj.toString().toLowerCase(Locale.ROOT);
    }

    public abstract MappingsNamespace getTargetNamespace();

    protected String getVersion() {
        @Nullable
        final MappingConfiguration mappingConfig = extension.getMappingConfiguration();

        return "%s%s"
            .formatted(
                extension.getZomboidProvider().clientZomboidVersion(),
                Objects.nonNull(mappingConfig) ? "-" + mappingConfig.mappingsIdentifier() : "");
    }

    protected String getDependencyNotation(ZomboidJar.Type type) {
        return "com.theindiestone:%s:%s".formatted(getName(type), getVersion());
    }

    private boolean areOutputsValid(List<RemappedJars> remappedJars) {
        for (RemappedJars remappedJar : remappedJars) {
            if (!getMavenHelper(remappedJar.type()).exists(null)) {
                return false;
            }
        }

        return true;
    }

    private void remapInputs(List<RemappedJars> remappedJars, ConfigContext configContext) throws IOException {
        cleanOutputs(remappedJars);

        for (RemappedJars remappedJar : remappedJars) {
            remapJar(remappedJar, configContext);
        }
    }

    private void remapJar(RemappedJars remappedJars, ConfigContext configContext) throws IOException {
        final MappingConfiguration mappingConfiguration = extension.getMappingConfiguration();
        final String fromM = remappedJars.sourceNamespace().toString();
        final String toM = getTargetNamespace().toString();

        Files.deleteIfExists(remappedJars.outputJarPath());

        final Map<String, String> remappedSignatures = SignatureFixerApplyVisitor.getRemappedSignatures(
            getTargetNamespace() == MappingsNamespace.OFFICIAL,
            mappingConfiguration,
            getProject(),
            configContext.serviceFactory(),
            toM);
        final boolean fixRecords = zomboidProvider.getClientVersionInfo().javaVersion() >= 16;

        TinyRemapper remapper = TinyRemapperHelper.getTinyRemapper(
            getProject(), configContext.serviceFactory(), fromM, toM, fixRecords, (builder) -> {
                builder.extraPostApplyVisitor(new SignatureFixerApplyVisitor(remappedSignatures));
                configureRemapper(remappedJars, builder);
            });

        try (OutputConsumerPath outputConsumer = new OutputConsumerPath.Builder(remappedJars.outputJarPath()).build()) {
            outputConsumer.addNonClassFiles(remappedJars.inputJar());

            for (Path path : remappedJars.remapClasspath()) {
                remapper.readClassPath(path);
            }

            remapper.readInputs(remappedJars.inputJar());
            remapper.apply(outputConsumer);
        } catch (Exception e) {
            throw new RuntimeException(
                "Failed to remap JAR " + remappedJars.inputJar() + " with mappings from "
                + mappingConfiguration.tinyMappings,
                e);
        } finally {
            remapper.finish();
        }

        getMavenHelper(remappedJars.type()).savePom();
    }

    protected void configureRemapper(RemappedJars remappedJars, TinyRemapper.Builder tinyRemapperBuilder) {
    }

    private void cleanOutputs(List<RemappedJars> remappedJars) throws IOException {
        for (RemappedJars remappedJar : remappedJars) {
            Files.deleteIfExists(remappedJar.outputJarPath());
            Files.deleteIfExists(getBackupJarPath(remappedJar.outputJar()));
        }
    }

    public Project getProject() {
        return project;
    }

    public M getZomboidProvider() {
        return zomboidProvider;
    }

    public enum MavenScope {
        // Output files will be stored per project
        LOCAL(LoomFiles::getLocalZomboidRepo),
        // Output files will be stored globally
        GLOBAL(LoomFiles::getGlobalZomboidRepo);

        private final Function<LoomFiles, File> fileFunction;

        MavenScope(Function<LoomFiles, File> fileFunction) {
            this.fileFunction = fileFunction;
        }

        public Path getRoot(LoomGradleExtension extension) {
            return fileFunction.apply(extension.getFiles()).toPath();
        }
    }

    public record ProvideContext(boolean applyDependencies, boolean refreshOutputs, ConfigContext configContext) {
        ProvideContext withApplyDependencies(boolean applyDependencies) {
            return new ProvideContext(applyDependencies, refreshOutputs(), configContext());
        }
    }

    public record RemappedJars(
        Path inputJar, ZomboidJar outputJar, MappingsNamespace sourceNamespace, Path... remapClasspath) {
        public Path outputJarPath() {
            return outputJar().getPath();
        }

        public String name() {
            return outputJar().getName();
        }

        public ZomboidJar.Type type() {
            return outputJar().getType();
        }
    }
}

/*
 * This file is part of leaf-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021-2024 aoqia, FabricMC
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

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.stream.Stream;
import javax.inject.Inject;
import net.fabricmc.accesswidener.AccessWidenerReader;
import net.fabricmc.accesswidener.AccessWidenerRemapper;
import net.fabricmc.accesswidener.AccessWidenerWriter;
import dev.aoqia.leaf.loom.LoomGradleExtension;
import dev.aoqia.leaf.loom.build.nesting.JarNester;
import dev.aoqia.leaf.loom.build.nesting.NestableJarGenerationTask;
import dev.aoqia.leaf.loom.configuration.accesswidener.AccessWidenerFile;
import dev.aoqia.leaf.loom.configuration.mods.ArtifactMetadata;
import dev.aoqia.leaf.loom.task.service.ClientEntriesService;
import dev.aoqia.leaf.loom.task.service.MixinRefmapService;
import dev.aoqia.leaf.loom.task.service.TinyRemapperService;
import dev.aoqia.leaf.loom.util.Constants;
import dev.aoqia.leaf.loom.util.ExceptionUtil;
import dev.aoqia.leaf.loom.util.Pair;
import dev.aoqia.leaf.loom.util.SidedClassVisitor;
import dev.aoqia.leaf.loom.util.ZipUtils;
import dev.aoqia.leaf.loom.util.fmj.LeafModJsonFactory;
import dev.aoqia.leaf.loom.util.fmj.LeafModJsonUtils;
import dev.aoqia.leaf.loom.util.service.ScopedServiceFactory;
import dev.aoqia.leaf.loom.util.service.ServiceFactory;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskProvider;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class RemapJarTask extends AbstractRemapJarTask {
    @InputFiles
    public abstract ConfigurableFileCollection getNestedJars();

    @Input
    public abstract Property<Boolean> getAddNestedDependencies();

    /**
     * Whether to optimize the leaf.mod.json file, by default this is false.
     *
     * <p>The schemaVersion entry will be placed first in the json file
     */
    @Input
    public abstract Property<Boolean> getOptimizeLeafModJson();

    @Input
    @ApiStatus.Internal
    public abstract Property<Boolean> getUseMixinAP();

    @Nested
    public abstract Property<TinyRemapperService.Options> getTinyRemapperServiceOptions();

    @Nested
    public abstract ListProperty<MixinRefmapService.Options> getMixinRefmapServiceOptions();

    @Inject
    public RemapJarTask() {
        super();
        final ConfigurationContainer configurations = getProject().getConfigurations();
        getClasspath().from(configurations.getByName(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME));
        getAddNestedDependencies().convention(true).finalizeValueOnRead();
        getOptimizeLeafModJson().convention(false).finalizeValueOnRead();

        TaskProvider<NestableJarGenerationTask> processIncludeJars =
                getProject().getTasks().named(Constants.Task.PROCESS_INCLUDE_JARS, NestableJarGenerationTask.class);
        getNestedJars().from(getProject().fileTree(processIncludeJars.get().getOutputDirectory()));
        getNestedJars().builtBy(processIncludeJars);

        getUseMixinAP().set(LoomGradleExtension.get(getProject()).getMixin().getUseLegacyMixinAp());

        // Make outputs reproducible by default
        setReproducibleFileOrder(true);
        setPreserveFileTimestamps(false);

        getJarType().set("classes");

        getTinyRemapperServiceOptions().set(TinyRemapperService.createOptions(this));
        getMixinRefmapServiceOptions().set(MixinRefmapService.createOptions(this));
    }

    @TaskAction
    public void run() {
        submitWork(RemapAction.class, params -> {
            if (getAddNestedDependencies().get()) {
                params.getNestedJars().from(getNestedJars());
            }

            if (!params.namespacesMatch()) {
                params.getTinyRemapperServiceOptions().set(getTinyRemapperServiceOptions());
                params.getMixinRefmapServiceOptions().set(getMixinRefmapServiceOptions());

                params.getRemapClasspath().from(getClasspath());

                final boolean mixinAp = getUseMixinAP().get();
                params.getUseMixinExtension().set(!mixinAp);

                // Add the mixin refmap remap type to the manifest
                // This is used by the mod dependency remapper to determine if it should remap the refmap
                // or if the refmap should be remapped by mixin at runtime.
                final var refmapRemapType =
                        mixinAp ? ArtifactMetadata.MixinRemapType.MIXIN : ArtifactMetadata.MixinRemapType.STATIC;
                params.getManifestAttributes()
                        .put(Constants.Manifest.MIXIN_REMAP_TYPE, refmapRemapType.manifestValue());
            }

            params.getOptimizeFmj().set(getOptimizeLeafModJson().get());
        });
    }

    public interface RemapParams extends AbstractRemapParams {
        ConfigurableFileCollection getNestedJars();

        ConfigurableFileCollection getRemapClasspath();

        Property<Boolean> getUseMixinExtension();

        Property<Boolean> getOptimizeFmj();

        Property<TinyRemapperService.Options> getTinyRemapperServiceOptions();

        ListProperty<MixinRefmapService.Options> getMixinRefmapServiceOptions();
    }

    public abstract static class RemapAction extends AbstractRemapAction<RemapParams> {
        private static final Logger LOGGER = LoggerFactory.getLogger(RemapAction.class);

        private @Nullable TinyRemapperService tinyRemapperService;
        private @Nullable TinyRemapper tinyRemapper;

        public RemapAction() {}

        @Override
        public void execute() {
            try (var serviceFactory = new ScopedServiceFactory()) {
                LOGGER.info("Remapping {} to {}", inputFile, outputFile);

                this.tinyRemapperService =
                        getParameters().getTinyRemapperServiceOptions().isPresent()
                                ? serviceFactory.get(getParameters()
                                        .getTinyRemapperServiceOptions()
                                        .get())
                                : null;

                prepare();

                if (tinyRemapperService != null) {
                    tinyRemapper = tinyRemapperService.getTinyRemapperForRemapping();

                    remap();
                } else {
                    Files.copy(inputFile, outputFile, StandardCopyOption.REPLACE_EXISTING);
                }

                if (getParameters().getClientOnlyEntries().isPresent()) {
                    markClientOnlyClasses();
                }

                remapAccessWidener();
                addRefmaps(serviceFactory);
                addNestedJars();
                modifyJarManifest();
                rewriteJar();

                if (getParameters().getOptimizeFmj().get()) {
                    optimizeFMJ();
                }

                if (tinyRemapperService != null) {
                    tinyRemapperService.close();
                }

                LOGGER.debug("Finished remapping {}", inputFile);
            } catch (Exception e) {
                try {
                    Files.deleteIfExists(outputFile);
                } catch (IOException ex) {
                    LOGGER.error("Failed to delete output file", ex);
                }

                throw ExceptionUtil.createDescriptiveWrapper(RuntimeException::new, "Failed to remap", e);
            }
        }

        private void prepare() {
            final Path inputFile =
                    getParameters().getInputFile().getAsFile().get().toPath();

            if (tinyRemapperService != null) {
                tinyRemapperService
                        .getTinyRemapperForInputs()
                        .readInputsAsync(tinyRemapperService.getOrCreateTag(inputFile), inputFile);
            }
        }

        private void remap() throws IOException {
            Objects.requireNonNull(tinyRemapperService, "tinyRemapperService");
            Objects.requireNonNull(tinyRemapper, "tinyRemapper");

            // Delete the old file to prevent deleted contents from sticking around in the jar.
            Files.deleteIfExists(outputFile);

            try (OutputConsumerPath outputConsumer = new OutputConsumerPath.Builder(outputFile).build()) {
                outputConsumer.addNonClassFiles(inputFile);
                tinyRemapper.apply(outputConsumer, tinyRemapperService.getOrCreateTag(inputFile));
            }
        }

        private void markClientOnlyClasses() throws IOException {
            final Stream<Pair<String, ZipUtils.UnsafeUnaryOperator<byte[]>>> tranformers =
                    getParameters().getClientOnlyEntries().get().stream()
                            .map(s -> new Pair<>(s, (ZipUtils.AsmClassOperator)
                                    classVisitor -> SidedClassVisitor.CLIENT.insertApplyVisitor(null, classVisitor)));

            ZipUtils.transform(outputFile, tranformers);
        }

        private void remapAccessWidener() throws IOException {
            if (getParameters().namespacesMatch()) {
                return;
            }

            final AccessWidenerFile accessWidenerFile = AccessWidenerFile.fromModJar(inputFile);

            if (accessWidenerFile == null) {
                return;
            }

            byte[] remapped = remapAccessWidener(accessWidenerFile.content());

            // Finally, replace the output with the remaped aw
            ZipUtils.replace(outputFile, accessWidenerFile.path(), remapped);
        }

        private byte[] remapAccessWidener(byte[] input) {
            Objects.requireNonNull(tinyRemapper, "tinyRemapper");

            int version = AccessWidenerReader.readVersion(input);

            AccessWidenerWriter writer = new AccessWidenerWriter(version);
            AccessWidenerRemapper remapper = new AccessWidenerRemapper(
                    writer,
                    tinyRemapper.getEnvironment().getRemapper(),
                    getParameters().getSourceNamespace().get(),
                    getParameters().getTargetNamespace().get());
            AccessWidenerReader reader = new AccessWidenerReader(remapper);
            reader.read(input);

            return writer.write();
        }

        private void addNestedJars() {
            FileCollection nestedJars = getParameters().getNestedJars();

            if (nestedJars.isEmpty()) {
                LOGGER.info("No jars to nest");
                return;
            }

            JarNester.nestJars(nestedJars.getFiles(), outputFile.toFile(), LOGGER);
        }

        private void addRefmaps(ServiceFactory serviceFactory) throws IOException {
            if (getParameters().getUseMixinExtension().getOrElse(false)) {
                return;
            }

            for (MixinRefmapService.Options options :
                    getParameters().getMixinRefmapServiceOptions().get()) {
                MixinRefmapService mixinRefmapService = serviceFactory.get(options);
                mixinRefmapService.applyToJar(outputFile);
            }
        }

        private void optimizeFMJ() throws IOException {
            if (!ZipUtils.contains(outputFile, LeafModJsonFactory.LEAF_MOD_JSON)) {
                return;
            }

            ZipUtils.transformJson(
                    JsonObject.class,
                    outputFile,
                    LeafModJsonFactory.LEAF_MOD_JSON,
                    LeafModJsonUtils::optimizeFmj);
        }
    }

    @Override
    protected Provider<? extends ClientEntriesService.Options> getClientOnlyEntriesOptionsProvider(
            SourceSet clientSourceSet) {
        return ClientEntriesService.Classes.createOptions(getProject(), clientSourceSet);
    }
}

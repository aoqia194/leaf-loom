/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016-2023 FabricMC
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

package net.aoqia.loom.configuration;

import javax.inject.Inject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Consumer;

import net.aoqia.loom.LoomGradleExtension;
import net.aoqia.loom.api.InterfaceInjectionExtensionAPI;
import net.aoqia.loom.build.mixin.GroovyApInvoker;
import net.aoqia.loom.build.mixin.JavaApInvoker;
import net.aoqia.loom.build.mixin.KaptApInvoker;
import net.aoqia.loom.build.mixin.ScalaApInvoker;
import net.aoqia.loom.configuration.accesswidener.AccessWidenerJarProcessor;
import net.aoqia.loom.configuration.ifaceinject.InterfaceInjectionProcessor;
import net.aoqia.loom.configuration.processors.ModJavadocProcessor;
import net.aoqia.loom.configuration.processors.ZomboidJarProcessorManager;
import net.aoqia.loom.configuration.providers.mappings.LayeredMappingsFactory;
import net.aoqia.loom.configuration.providers.mappings.MappingConfiguration;
import net.aoqia.loom.configuration.providers.zomboid.ZomboidMetadataProvider;
import net.aoqia.loom.configuration.providers.zomboid.ZomboidProvider;
import net.aoqia.loom.configuration.providers.zomboid.ZomboidSourceSets;
import net.aoqia.loom.configuration.providers.zomboid.mapped.AbstractMappedZomboidProvider;
import net.aoqia.loom.configuration.providers.zomboid.mapped.NamedZomboidProvider;
import net.aoqia.loom.extension.MixinExtension;
import net.aoqia.loom.util.Checksum;
import net.aoqia.loom.util.Constants;
import net.aoqia.loom.util.ExceptionUtil;
import net.aoqia.loom.util.ProcessUtil;
import net.aoqia.loom.util.gradle.GradleUtils;
import net.aoqia.loom.util.gradle.SourceSetHelper;
import net.aoqia.loom.util.service.ScopedServiceFactory;
import net.aoqia.loom.util.service.ServiceFactory;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.AbstractCopyTask;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.javadoc.Javadoc;

import static net.aoqia.loom.util.Constants.Configurations;

public abstract class CompileConfiguration implements Runnable {
    private static Duration getDefaultTimeout() {
        if (System.getenv("CI") != null) {
            // Set a small timeout on CI, as it's unlikely going to unlock.
            return Duration.ofMinutes(1);
        }

        return Duration.ofHours(1);
    }

    @Inject
    protected abstract Project getProject();

    @Inject
    protected abstract TaskContainer getTasks();

    @Override
    public void run() {
        LoomGradleExtension extension = LoomGradleExtension.get(getProject());

        getTasks().named(JavaPlugin.JAVADOC_TASK_NAME, Javadoc.class).configure(javadoc -> {
            final SourceSet main = SourceSetHelper.getMainSourceSet(getProject());
            javadoc.setClasspath(main.getOutput().plus(main.getCompileClasspath()));
        });

        afterEvaluationWithService((serviceFactory) -> {
            final ConfigContext configContext = new ConfigContextImpl(getProject(), serviceFactory, extension);

            ZomboidSourceSets.get(getProject()).afterEvaluate(getProject());
            final boolean previousRefreshDeps = extension.refreshDeps();

            final LockResult lockResult = acquireProcessLockWaiting(getLockFile());
            if (lockResult != LockResult.ACQUIRED_CLEAN) {
                getProject()
                    .getLogger()
                    .lifecycle(
                        "Found existing cache lock file ({}), rebuilding loom cache. This may have been "
                        + "caused by a failed or canceled build.",
                        lockResult);
                extension.setRefreshDeps(true);
            }

            try {
                setupZomboid(configContext);

                if (!GradleUtils.getBooleanProperty(getProject(), Constants.Properties.ONLY_PROVIDE_JARS)) {
                    LoomDependencyManager dependencyManager = new LoomDependencyManager();
                    extension.setDependencyManager(dependencyManager);
                    dependencyManager.handleDependencies(getProject(), serviceFactory);
                }
            } catch (Exception e) {
                ExceptionUtil.processException(e, getProject());
                disownLock();
                throw ExceptionUtil.createDescriptiveWrapper(RuntimeException::new, "Failed to setup Zomboid", e);
            }

            releaseLock();
            extension.setRefreshDeps(previousRefreshDeps);

            // If only providing jars, we dont do mixin stuff or decompile tasks.
            if (GradleUtils.getBooleanProperty(getProject(), Constants.Properties.ONLY_PROVIDE_JARS)) {
                return;
            }

            MixinExtension mixin = LoomGradleExtension.get(getProject()).getMixin();

            if (mixin.getUseLegacyMixinAp().get()) {
                setupMixinAp(mixin);
            }

            configureDecompileTasks(configContext);
        });

        finalizedBy("eclipse", "genEclipseRuns");

        // Add the "dev" jar to the "namedElements" configuration
        getProject()
            .artifacts(artifactHandler -> artifactHandler.add(
                Configurations.NAMED_ELEMENTS, getTasks().named("jar")));

        // Ensure that the encoding is set to UTF-8, no matter what the system default is
        // this fixes some edge cases with special characters not displaying correctly
        // see http://yodaconditions.net/blog/fix-for-java-file-encoding-problems-with-gradle.html
        getTasks()
            .withType(AbstractCopyTask.class)
            .configureEach(abstractCopyTask -> abstractCopyTask.setFilteringCharset(StandardCharsets.UTF_8.name()));
        getTasks()
            .withType(JavaCompile.class)
            .configureEach(javaCompile -> javaCompile.getOptions().setEncoding(StandardCharsets.UTF_8.name()));

        if (getProject().getPluginManager().hasPlugin("org.jetbrains.kotlin.kapt")) {
            // If loom is applied after kapt, then kapt will use the AP arguments too early for loom to pass the
            // arguments we need for mixin.
            throw new IllegalArgumentException("leaf-loom must be applied BEFORE kapt in the plugins { } block.");
        }
    }

    // This is not thread safe across getProject()s synchronize it here just to be sure, might be possible to move
    // this further down, but for now this will do.
    private synchronized void setupZomboid(ConfigContext configContext) throws Exception {
        final Project project = configContext.project();
        final LoomGradleExtension extension = configContext.extension();

        final ZomboidMetadataProvider clientMetadataProvider = ZomboidMetadataProvider.create(false, configContext);
        extension.setClientMetadataProvider(clientMetadataProvider);
        final ZomboidMetadataProvider serverMetadataProvider = ZomboidMetadataProvider.create(true, configContext);
        extension.setServerMetadataProvider(serverMetadataProvider);

        var jarConfiguration = extension.getZomboidJarConfiguration().get();

        // Provide the zomboid jars
        final ZomboidProvider zomboidProvider = jarConfiguration.createZomboidProvider(
            clientMetadataProvider, serverMetadataProvider, configContext);
        extension.setZomboidProvider(zomboidProvider);
        zomboidProvider.provide();

        // Don't do any more if only providing jars from ZomboidProvider.
        if (GradleUtils.getBooleanProperty(project, Constants.Properties.ONLY_PROVIDE_JARS)) {
            return;
        }

        // Created any layered mapping files.
        LayeredMappingsFactory.afterEvaluate(configContext);

        final DependencyInfo mappingsDep = DependencyInfo.create(getProject(), Configurations.MAPPINGS);
        final MappingConfiguration mappingConfiguration =
            MappingConfiguration.create(getProject(),
                configContext.serviceFactory(),
                mappingsDep,
                zomboidProvider);
        extension.setMappingConfiguration(mappingConfiguration);
        mappingConfiguration.applyToProject(getProject(), mappingsDep);

        NamedZomboidProvider<?> namedZomboidProvider = jarConfiguration.createNamedZomboidProvider(project);
        registerGameProcessors(configContext);

        ZomboidJarProcessorManager zomboidJarProcessorManager = ZomboidJarProcessorManager.create(getProject());
        if (zomboidJarProcessorManager != null) {
            // Wrap the named MC provider for one that will provide the processed jars
            namedZomboidProvider = jarConfiguration.createProcessedNamedZomboidProvider(
                namedZomboidProvider, zomboidJarProcessorManager);
        }

        final var provideContext = new AbstractMappedZomboidProvider.ProvideContext(true,
            extension.refreshDeps(),
            configContext);

        extension.setNamedZomboidProvider(namedZomboidProvider);
        namedZomboidProvider.provide(provideContext);
    }

    private void registerGameProcessors(ConfigContext configContext) {
        final LoomGradleExtension extension = configContext.extension();

        final boolean enableTransitiveAccessWideners =
            extension.getEnableTransitiveAccessWideners().get();
        extension.addZomboidJarProcessor(
            AccessWidenerJarProcessor.class,
            "leaf-loom:access-widener",
            enableTransitiveAccessWideners,
            extension.getAccessWidenerPath());

        if (extension.getEnableModProvidedJavadoc().get()) {
            extension.addZomboidJarProcessor(ModJavadocProcessor.class, "leaf-loom:mod-javadoc");
        }

        final InterfaceInjectionExtensionAPI interfaceInjection = extension.getInterfaceInjection();

        if (interfaceInjection.isEnabled()) {
            extension.addZomboidJarProcessor(
                InterfaceInjectionProcessor.class,
                "leaf-loom:interface-inject",
                interfaceInjection.getEnableDependencyInterfaceInjection().get());
        }
    }

    private void setupMixinAp(MixinExtension mixin) {
        mixin.init();

        // Disable some things used by log4j via the mixin AP that prevent it from being garbage collected
        System.setProperty("log4j2.disable.jmx", "true");
        System.setProperty("log4j.shutdownHookEnabled", "false");
        System.setProperty("log4j.skipJansi", "true");

        getProject().getLogger().info("Configuring compiler arguments for Java");

        new JavaApInvoker(getProject()).configureMixin();

        if (getProject().getPluginManager().hasPlugin("scala")) {
            getProject().getLogger().info("Configuring compiler arguments for Scala");
            new ScalaApInvoker(getProject()).configureMixin();
        }

        if (getProject().getPluginManager().hasPlugin("org.jetbrains.kotlin.kapt")) {
            getProject().getLogger().info("Configuring compiler arguments for Kapt plugin");
            new KaptApInvoker(getProject()).configureMixin();
        }

        if (getProject().getPluginManager().hasPlugin("groovy")) {
            getProject().getLogger().info("Configuring compiler arguments for Groovy");
            new GroovyApInvoker(getProject()).configureMixin();
        }
    }

    private void configureDecompileTasks(ConfigContext configContext) {
        final LoomGradleExtension extension = configContext.extension();

        extension
            .getZomboidJarConfiguration()
            .get()
            .createDecompileConfiguration(getProject())
            .afterEvaluation();
    }

    private LockFile getLockFile() {
        final LoomGradleExtension extension = LoomGradleExtension.get(getProject());
        final Path cacheDirectory = extension.getFiles().getUserCache().toPath();
        final String pathHash = Checksum.projectHash(getProject());
        return new LockFile(
            cacheDirectory.resolve("." + pathHash + ".lock"),
            "Lock for cache='%s', project='%s'"
                .formatted(
                    cacheDirectory,
                    getProject().absoluteProjectPath(getProject().getPath())));
    }

    private LockResult acquireProcessLockWaiting(LockFile lockFile) {
        // one hour
        return this.acquireProcessLockWaiting(lockFile, getDefaultTimeout());
    }

    private LockResult acquireProcessLockWaiting(LockFile lockFile, Duration timeout) {
        try {
            return this.acquireProcessLockWaiting_(lockFile, timeout);
        } catch (final IOException e) {
            throw new RuntimeException("Exception acquiring lock " + lockFile, e);
        }
    }

    // Returns true if our process already owns the lock
    @SuppressWarnings("BusyWait")
    private LockResult acquireProcessLockWaiting_(LockFile lockFile, Duration timeout) throws IOException {
        final long timeoutMs = timeout.toMillis();
        final Logger logger = Logging.getLogger("loom_acquireProcessLockWaiting");
        final long currentPid = ProcessHandle.current().pid();
        boolean abrupt = false;
        boolean disowned = false;

        if (Files.exists(lockFile.file)) {
            long lockingProcessId = -1;

            try {
                String lockValue = Files.readString(lockFile.file);

                if ("disowned".equals(lockValue)) {
                    disowned = true;
                } else {
                    lockingProcessId = Long.parseLong(lockValue);
                    logger.lifecycle("\"{}\" is currently held by pid '{}'.", lockFile, lockingProcessId);
                }
            } catch (final Exception ignored) {
                // ignored
            }

            if (lockingProcessId == currentPid) {
                return LockResult.ACQUIRED_ALREADY_OWNED;
            }

            Optional<ProcessHandle> handle = ProcessHandle.of(lockingProcessId);

            if (disowned) {
                logger.lifecycle("Previous process has disowned the lock due to abrupt termination.");
                Files.deleteIfExists(lockFile.file);
            } else if (handle.isEmpty()) {
                logger.lifecycle("Locking process does not exist, assuming abrupt termination and deleting lock file.");
                Files.deleteIfExists(lockFile.file);
                abrupt = true;
            } else {
                ProcessUtil processUtil = ProcessUtil.create(getProject());
                logger.lifecycle(processUtil.printWithParents(handle.get()));
                logger.lifecycle("Waiting for lock to be released...");
                long sleptMs = 0;

                while (Files.exists(lockFile.file)) {
                    try {
                        Thread.sleep(100);
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                    sleptMs += 100;

                    if (sleptMs >= 1000 * 60 && sleptMs % (1000 * 60) == 0L) {
                        logger.lifecycle(
                            """
                                Have been waiting on "{}" held by pid '{}' for {} minute(s).
                                If this persists for an unreasonable length of time, kill this process, run './gradlew --stop' and then try again.""",
                            lockFile,
                            lockingProcessId,
                            sleptMs / 1000 / 60);
                    }

                    if (sleptMs >= timeoutMs) {
                        throw new GradleException(
                            "Have been waiting on lock file '%s' for %s ms. Giving up as timeout is %s ms."
                                .formatted(lockFile, sleptMs, timeoutMs));
                    }
                }
            }
        }

        if (!Files.exists(lockFile.file.getParent())) {
            Files.createDirectories(lockFile.file.getParent());
        }

        Files.writeString(lockFile.file, String.valueOf(currentPid));

        if (disowned) {
            return LockResult.ACQUIRED_PREVIOUS_OWNER_DISOWNED;
        } else if (abrupt) {
            return LockResult.ACQUIRED_PREVIOUS_OWNER_MISSING;
        }

        return LockResult.ACQUIRED_CLEAN;
    }

    // When we fail to configure, write "disowned" to the lock file to release it from this process
    // This allows the next run to rebuild without waiting for this process to exit
    private void disownLock() {
        final Path lock = getLockFile().file;

        try {
            Files.writeString(lock, "disowned");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void releaseLock() {
        final Path lock = getLockFile().file;

        if (!Files.exists(lock)) {
            return;
        }

        try {
            Files.delete(lock);
        } catch (IOException e1) {
            try {
                // If we failed to delete the lock file, moving it before trying to delete it may help.
                final Path del = lock.resolveSibling(lock.getFileName() + ".del");
                Files.move(lock, del);
                Files.delete(del);
            } catch (IOException e2) {
                var exception = new UncheckedIOException("Failed to release getProject() configuration lock", e2);
                exception.addSuppressed(e1);
                throw exception;
            }
        }
    }

    private void finalizedBy(String a, String b) {
        getTasks().named(a).configure(task -> task.finalizedBy(getTasks().named(b)));
    }

    private void afterEvaluationWithService(Consumer<ServiceFactory> consumer) {
        GradleUtils.afterSuccessfulEvaluation(getProject(), () -> {
            try (var serviceFactory = new ScopedServiceFactory()) {
                consumer.accept(serviceFactory);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    enum LockResult {
        // acquired immediately or after waiting for another process to release
        ACQUIRED_CLEAN,
        // already owned by current pid
        ACQUIRED_ALREADY_OWNED,
        // acquired due to current owner not existing
        ACQUIRED_PREVIOUS_OWNER_MISSING,
        // acquired due to previous owner disowning the lock
        ACQUIRED_PREVIOUS_OWNER_DISOWNED
    }

    record LockFile(Path file, String description) {
        @Override
        public String toString() {
            return this.description;
        }
    }
}

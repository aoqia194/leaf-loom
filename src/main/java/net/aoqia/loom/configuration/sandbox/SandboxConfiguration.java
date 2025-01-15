/*
 * This file is part of leaf-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2024 aoqia, FabricMC
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
package net.aoqia.loom.configuration.sandbox;

import java.nio.file.Path;
import java.util.Objects;
import javax.inject.Inject;
import net.aoqia.loom.LoomGradleExtension;
import net.aoqia.loom.configuration.ide.RunConfigSettings;
import net.aoqia.loom.util.Constants;
import net.aoqia.loom.util.Platform;
import net.aoqia.loom.util.gradle.GradleUtils;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.dsl.DependencyFactory;
import org.gradle.api.plugins.JavaPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Allows the user to specify a sandbox maven artifact as a gradle property.
 * The sandbox jar is read to figure out if it's supported on the current platform.
 * If it is, its added to the runtime classpath and a new client run config is created
 */
public abstract class SandboxConfiguration implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(SandboxConfiguration.class);

    @Inject
    protected abstract Project getProject();

    @Inject
    public abstract DependencyFactory getDependencyFactory();

    @Override
    public void run() {
        LoomGradleExtension extension = LoomGradleExtension.get(getProject());

        if (extension.isProjectIsolationActive()) {
            LOGGER.debug("Skipping sandbox configuration as project isolation is enabled.");
            return;
        }

        if (getProject().findProperty(Constants.Properties.SANDBOX) == null) {
            LOGGER.debug("No fabric sandbox property set");
            return;
        }

        GradleUtils.afterSuccessfulEvaluation(getProject(), this::evaluate);
    }

    private void evaluate() {
        final String sandboxNotation =
                (String) Objects.requireNonNull(getProject().findProperty(Constants.Properties.SANDBOX));
        final LoomGradleExtension extension = LoomGradleExtension.get(getProject());
        final ExternalModuleDependency dependency = getDependencyFactory().create(sandboxNotation);
        final Configuration configuration = getProject().getConfigurations().detachedConfiguration(dependency);
        final Path sandboxJar = configuration.getSingleFile().toPath();
        final SandboxMetadata metadata = SandboxMetadata.readFromJar(sandboxJar);

        if (!metadata.supportsPlatform(Platform.CURRENT)) {
            LOGGER.info("Sandbox does not support the current platform");
            return;
        }

        getProject().getDependencies().add(JavaPlugin.RUNTIME_ONLY_CONFIGURATION_NAME, dependency);

        extension.getRuns().create("clientSandbox", settings -> {
            RunConfigSettings clientRun = extension.getRuns().getByName("client");

            settings.inherit(clientRun);

            settings.name("Client Sandbox");

            // The sandbox also acts as DLI
            // Set the sandbox as the true main class
            settings.devLaunchMainClass().set(metadata.mainClass());
            settings.property(
                    "leaf.sandbox.realMain", clientRun.getMainClass().get());
        });
    }
}

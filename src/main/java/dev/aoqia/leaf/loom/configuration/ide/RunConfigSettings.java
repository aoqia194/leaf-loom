/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021-2023 FabricMC
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

package dev.aoqia.leaf.loom.configuration.ide;

import javax.inject.Inject;

import org.gradle.api.Named;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;

import dev.aoqia.leaf.loom.api.RunConfiguration;

public abstract class RunConfigSettings implements Named, RunConfiguration, RunConfigurationInternal {
	// The base name of the run configuration, which is the name it is created with, i.e. 'client'
	private final String name;
	private final Project project;

	@Inject
	public RunConfigSettings(Project project, String name) {
		this.name = name;
		this.project = project;
		DefaultRunConfigurationSettings.configureDefaults(this, project);
	}

	@Override
	public String getName() {
		return name;
	}

	// Backwards compatibility shims:

	// Note: Overridden for backwards compatibility
	@Override
	public abstract Property<Boolean> getAppendProjectPathToDisplayName();

	// Note: Overridden for backwards compatibility
	@Override
	public abstract Property<String> getMainClass();

	// Note: Overridden for backwards compatibility
	@Override
	public void client() {
		RunConfigurationInternal.super.client();
	}

	// Note: Overridden for backwards compatibility
	@Override
	public void server() {
		RunConfigurationInternal.super.server();
	}

	// Note: Overload method for backwards compatibility
	public void inherit(RunConfigSettings parent) {
		RunConfigurationInternal.super.inherit(parent);
	}

	// Note: Overridden for backwards compatibility
	@Override
	public abstract Property<String> getIdeConfigFolder();

	// Deprecated methods:

	/**
	 * Technically deprecated, but as it shadows the "project" property from the parent class, it reports as deprecated in IDEs, which is undesirable.
	 */
	public Project getProject() {
		return project;
	}
}

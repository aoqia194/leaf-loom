/*
 * This file is part of leaf-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2022 aoqia, FabricMC
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
package dev.aoqia.leaf.loom.test.unit


import org.gradle.api.tasks.SourceSet
import spock.lang.Specification

import org.gradle.api.tasks.SourceSet
import spock.lang.Specification

import dev.aoqia.leaf.loom.api.RemapConfigurationSettings
import dev.aoqia.leaf.loom.configuration.RemapConfigurations
import dev.aoqia.leaf.loom.test.util.GradleTestUtil

import org.gradle.api.tasks.SourceSet
import spock.lang.Specification

class RemapConfigurationsTest extends Specification {
	private static final RemapConfigurations.ConfigurationOption IMPLEMENTATION_OPTION = new RemapConfigurations.ConfigurationOption(SourceSet.&getImplementationConfigurationName, true, true, RemapConfigurationSettings.PublishingMode.RUNTIME_ONLY)

	def "testmod impl name"() {
		given:
		def sourceSet = GradleTestUtil.mockSourceSet("testmod")
		when:
		def name = IMPLEMENTATION_OPTION.name(sourceSet)
		then:
		name == "modTestmodImplementation"
	}

	def "main impl name"() {
		given:
		def sourceSet = GradleTestUtil.mockSourceSet("main")
		when:
		def name = IMPLEMENTATION_OPTION.name(sourceSet)
		then:
		name == "modImplementation"
	}
}

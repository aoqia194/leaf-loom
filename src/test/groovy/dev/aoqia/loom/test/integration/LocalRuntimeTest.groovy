/*
 * This file is part of leaf-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2018-2021 aoqia, FabricMC
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
package dev.aoqia.loom.test.integration

import spock.lang.Specification
import spock.lang.Unroll

import spock.lang.Specification
import spock.lang.Unroll

import dev.aoqia.loom.test.util.GradleProjectTestTrait

import static dev.aoqia.loom.test.LoomTestConstants.STANDARD_TEST_VERSIONS
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class LocalRuntimeTest extends Specification implements GradleProjectTestTrait {
	@Unroll
	def "build (gradle #version)"() {
		setup:
		def gradle = gradleProject(project: "localRuntime", version: version)

		when:
		def result = gradle.run(tasks: [
			"build",
			"publishToMavenLocal"
		])

		then:
		result.task(":build").outcome == SUCCESS
		def pomFile = new File(gradle.getProjectDir(), "build/publications/mavenJava/pom-default.xml")
		def pom = pomFile.text
		!pom.contains("leaf-api")
		!pom.contains("enigma")

		where:
		version << STANDARD_TEST_VERSIONS
	}
}

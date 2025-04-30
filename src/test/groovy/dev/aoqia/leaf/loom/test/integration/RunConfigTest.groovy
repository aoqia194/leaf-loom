/*
 * This file is part of leaf-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2018-2023 aoqia, FabricMC
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
package dev.aoqia.leaf.loom.test.integration

import dev.aoqia.loom.test.util.GradleProjectTestTrait
import spock.lang.Specification
import spock.lang.Unroll
import spock.util.environment.RestoreSystemProperties

import dev.aoqia.loom.test.util.GradleProjectTestTrait
import spock.lang.Specification
import spock.lang.Unroll
import spock.util.environment.RestoreSystemProperties

import dev.aoqia.loom.test.util.GradleProjectTestTrait
import spock.lang.Specification
import spock.lang.Unroll
import spock.util.environment.RestoreSystemProperties

import static dev.aoqia.loom.test.LoomTestConstants.STANDARD_TEST_VERSIONS
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

// This test runs a mod that exits on mod init
class RunConfigTest extends Specification implements GradleProjectTestTrait {
	private static List<String> tasks = [
		"runClient",
		"runServer",
		"runTestmodClient",
		"runTestmodServer",
		"runAutoTestServer"
	]
	@Unroll
	def "Run config #task (gradle #version)"() {
		setup:
		def gradle = gradleProject(project: "runconfigs", sharedFiles: true, version: version)

		when:
		def result = gradle.run(task: task)

		then:
		result.task(":${task}").outcome == SUCCESS
		result.output.contains("This contains a space")

		where:
		version << STANDARD_TEST_VERSIONS * tasks.size()
		task << tasks * STANDARD_TEST_VERSIONS.size()
	}

	@Unroll
	def "Custom main class (gradle #version)"() {
		setup:
		def gradle = gradleProject(project: "runconfigs", sharedFiles: true, version: version)

		when:
		def result = gradle.run(task: 'runCustomMain')

		then:
		result.task(':runCustomMain').outcome == SUCCESS
		result.output.contains('hello custom main')

		where:
		version << STANDARD_TEST_VERSIONS
	}

	@RestoreSystemProperties
	@Unroll
	def "idea auto configuration (gradle #version)"() {
		setup:
		System.setProperty("idea.sync.active", "true")
		def gradle = gradleProject(project: "minimalBase", version: version)

		new File(gradle.projectDir, ".idea").mkdirs()

		gradle.buildGradle << '''
                dependencies {
                    zomboid "com.theindiestone:zomboid:41.78.16"
                    mappings "dev.aoqia:leaf-yarn:0.1.0+build.1:v2"
                    modImplementation "dev.aoqia:leaf-loader:0.1.0"
                }
            '''

		when:
		// Dont run with any tasks, the idea sync task should be invoked automatically due to the system prop
		def result = gradle.run(tasks: [])

		then:
		result.task(":ideaSyncTask").outcome == SUCCESS

		where:
		version << STANDARD_TEST_VERSIONS
	}

	// Test that the download assets task doesnt depend on a client run existing.
	@Unroll
	def "cleared runs (gradle #version)"() {
		setup:
		def gradle = gradleProject(project: "minimalBase", version: version)

		gradle.buildGradle << '''
                dependencies {
                    zomboid "com.theindiestone:zomboid:41.78.16"
                    mappings "dev.aoqia:leaf-yarn:0.1.0+build.1:v2"
                    modImplementation "dev.aoqia:leaf-loader:0.1.0"
                }

                loom {
                    runs.clear()
                }
            '''

		when:
		def result = gradle.run(tasks: ["downloadAssets"])

		then:
		result.task(":downloadAssets").outcome == SUCCESS

		where:
		version << STANDARD_TEST_VERSIONS
	}
}

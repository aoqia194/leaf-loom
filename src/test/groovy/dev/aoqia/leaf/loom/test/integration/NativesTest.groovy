/*
 * This file is part of leaf-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021 aoqia, FabricMC
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
import static org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE

class NativesTest extends Specification implements GradleProjectTestTrait  {
	@Unroll
	def "Default natives for 1.18 (gradle #version)"() {
		setup:
		def gradle = gradleProject(project: "minimalBase", version: version)

		gradle.buildGradle << '''
                    dependencies {
                        zomboid "com.theindiestone:zomboid:1.18"
                        mappings loom.officialZomboidMappings()
                    }
                '''

		when:
		// Run the task twice to ensure its up to date
		def result = gradle.run(task: "extractNatives")
		def result2 = gradle.run(task: "extractNatives")

		then:
		result.task(":extractNatives").outcome == SUCCESS
		result2.task(":extractNatives").outcome == UP_TO_DATE

		where:
		version << STANDARD_TEST_VERSIONS
	}

	@RestoreSystemProperties
	@Unroll
	def "Overridden natives for 1.18 (gradle #version)"() {
		setup:
		System.setProperty('loom.test.lwjgloverride', "true")
		def gradle = gradleProject(project: "minimalBase", version: version)

		gradle.buildGradle << '''
                    dependencies {
                        zomboid "com.theindiestone:zomboid:1.18"
                        mappings loom.officialZomboidMappings()
                    }
                '''

		when:
		// Run the task twice to ensure its up to date
		def result = gradle.run(task: "extractNatives")

		then:
		result.task(":extractNatives").outcome == SUCCESS

		where:
		version << STANDARD_TEST_VERSIONS
	}

	@RestoreSystemProperties
	@Unroll
	def "1.19 classpath natives (gradle #version)"() {
		setup:
		def gradle = gradleProject(project: "minimalBase", version: version)

		gradle.buildGradle << '''
                loom {
                    noIntermediateMappings()
                }

                dependencies {
                    zomboid "com.theindiestone:zomboid:41.78.16"
                    mappings loom.layered() {
                        // No names
                    }
                }
                '''

		when:
		// Run the task twice to ensure its up to date
		def result = gradle.run(task: "build")

		then:
		result.task(":build").outcome == SUCCESS

		where:
		version << STANDARD_TEST_VERSIONS
	}
}

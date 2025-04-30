/*
 * This file is part of leaf-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2018-2022 aoqia, FabricMC
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

import java.nio.file.Path

import dev.aoqia.loom.test.util.GradleProjectTestTrait
import spock.lang.Specification
import spock.lang.Unroll

import dev.aoqia.loom.test.util.GradleProjectTestTrait
import spock.lang.Specification
import spock.lang.Unroll

import dev.aoqia.loom.test.util.GradleProjectTestTrait
import spock.lang.Specification
import spock.lang.Unroll

import static dev.aoqia.loom.test.LoomTestConstants.PRE_RELEASE_GRADLE
import static dev.aoqia.loom.test.LoomTestConstants.STANDARD_TEST_VERSIONS
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class LegacyProjectTest extends Specification implements GradleProjectTestTrait {
	@Unroll
	def "legacy build (gradle #version)"() {
		setup:
		def gradle = gradleProject(project: "legacy", version: version)

		when:
		def result = gradle.run(task: "build")

		then:
		result.task(":build").outcome == SUCCESS

		where:
		version << STANDARD_TEST_VERSIONS
	}

	@Unroll
	def "Unsupported Project Zomboid (zomboid #version)"() {
		setup:
		def gradle = gradleProject(project: "minimalBase", version: PRE_RELEASE_GRADLE)
		gradle.buildGradle << """
                loom {
                    noIntermediateMappings()
                }

                dependencies {
                    zomboid "com.theindiestone:zomboid:${version}"
                    mappings loom.layered() {
                        // No names
                    }

                    modImplementation "dev.aoqia:leaf-loader:0.1.0"
                }
            """

		when:
		def result = gradle.run(task: "configureClientLaunch")

		then:
		result.task(":configureClientLaunch").outcome == SUCCESS

		where:
		version | _
		'41.78.16' | _
		'42.3.1-unstable.26566' | _
	}

	@Unroll
	def "Ancient minecraft (minecraft #version)"() {
		setup:
		def gradle = gradleProject(project: "minimalBase", version: PRE_RELEASE_GRADLE)
		gradle.buildGradle << """
                loom {
                    noIntermediateMappings()
                    clientOnlyZomboidJar()
                }

                dependencies {
                    zomboid "com.theindiestone:zomboid:${version}"
                    mappings loom.layered() {
                        // No names
                    }

                    modImplementation "dev.aoqia:leaf-loader:0.1.0"
                }
            """

		when:
		def result = gradle.run(task: "configureClientLaunch")

		then:
		result.task(":configureClientLaunch").outcome == SUCCESS

		where:
		version | _
		'40.43' | _
	}
}

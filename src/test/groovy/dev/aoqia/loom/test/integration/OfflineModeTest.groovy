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
package dev.aoqia.loom.test.integration

import spock.lang.Specification
import spock.lang.Unroll

import spock.lang.Specification
import spock.lang.Unroll

import dev.aoqia.loom.test.util.GradleProjectTestTrait

import static dev.aoqia.loom.test.LoomTestConstants.PRE_RELEASE_GRADLE
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class OfflineModeTest extends Specification implements GradleProjectTestTrait {
	// Test offline mode when running on a previously locked project
	@Unroll
	def "Offline refresh deps"() {
		setup:
		def gradle = gradleProject(project: "minimalBase", version: PRE_RELEASE_GRADLE)
		gradle.buildGradle << """
            dependencies {
                zomboid 'com.theindiestone:zomboid:41.78.16'
                mappings 'dev.aoqia:leaf-yarn:0.1.0+build.1:v2'
                modImplementation 'dev.aoqia:leaf-loader:0.1.0'
                modImplementation 'dev.aoqia:leaf-api:0.1.0+41.78.16'
            }

            import net.util.loom.aoqia.Checksum
            def projectHash = Checksum.projectHash(getProject())
            println("%%" + projectHash + "%%")

            """.stripIndent()
		when:
		// Normal online run to populate the caches
		def result1 = gradle.run(task: "build")

		def projectHash = result1.output.split("%%")[1]

		// Create a dummy lock file to ensure that the loom cache is rebuilt on the next run
		def lockFile = new File(gradle.gradleHomeDir, "caches/leaf-loom/.${projectHash}.lock")
		lockFile.text = "12345"

		// Run with --offline to ensure that nothing is downloaded.
		def result2 = gradle.run(tasks: ["clean", "build"], args: ["--offline"])
		then:
		result1.task(":build").outcome == SUCCESS
		result2.task(":build").outcome == SUCCESS

		result2.output.contains("is currently held by pid '12345'")
		result2.output.contains("rebuilding loom cache")
	}
}

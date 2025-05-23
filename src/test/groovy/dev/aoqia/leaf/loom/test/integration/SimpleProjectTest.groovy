/*
 * This file is part of leaf-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016-2021 aoqia, FabricMC
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

import java.util.concurrent.TimeUnit

import dev.aoqia.loom.test.util.GradleProjectTestTrait
import dev.aoqia.loom.test.util.ServerRunner
import spock.lang.Specification
import spock.lang.Timeout
import spock.lang.Unroll

import dev.aoqia.loom.test.util.GradleProjectTestTrait
import dev.aoqia.loom.test.util.ServerRunner
import spock.lang.Specification
import spock.lang.Timeout
import spock.lang.Unroll

import dev.aoqia.loom.test.util.GradleProjectTestTrait
import dev.aoqia.loom.test.util.ServerRunner
import spock.lang.Specification
import spock.lang.Timeout
import spock.lang.Unroll

import static dev.aoqia.loom.test.LoomTestConstants.PRE_RELEASE_GRADLE
import static dev.aoqia.loom.test.LoomTestConstants.STANDARD_TEST_VERSIONS
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

@Timeout(value = 20, unit = TimeUnit.MINUTES)
class SimpleProjectTest extends Specification implements GradleProjectTestTrait {
	@Unroll
	def "build and run (gradle #version)"() {
		setup:
		def gradle = gradleProject(project: "simple", version: version)
		gradle.buildSrc("remapext") // apply the remap extension plugin

		def server = ServerRunner.create(gradle.projectDir, "41.78.16")
				.withMod(gradle.getOutputFile("leaf-example-mod-1.0.0.jar"))
				.withLeafApi()
		when:
		def result = gradle.run(task: "build")
		def serverResult = server.run()
		then:
		result.task(":build").outcome == SUCCESS
		gradle.getOutputZipEntry("leaf-example-mod-1.0.0-no-remap.jar", "META-INF/MANIFEST.MF").contains("Leaf-Loom-Version: 0.0.0+unknown")
		gradle.getOutputZipEntry("leaf-example-mod-1.0.0-no-remap-sources.jar", "dev/aoqia/example/mixin/ExampleMixin.java").contains("MainScreenState.class") // Very basic test to ensure sources did not get remapped :)

		serverResult.successful()
		serverResult.output.contains("Hello simple Leaf mod") // A check to ensure our mod init was actually called
		serverResult.output.contains("Hello Loom!") // Check that the remapper extension worked
		where:
		version << STANDARD_TEST_VERSIONS
	}

	@Unroll
	def "#ide config generation"() {
		setup:
		def gradle = gradleProject(project: "simple", sharedFiles: true)
		when:
		def result = gradle.run(task: ide)
		then:
		result.task(":${ide}").outcome == SUCCESS
		where:
		ide 				| _
		'ideaSyncTask' 		| _
		'genEclipseRuns'	| _
		'vscode'			| _
	}

	@Unroll
	def "remap mixins with tiny-remapper"() {
		setup:
		def gradle = gradleProject(project: "simple", version: PRE_RELEASE_GRADLE)
		gradle.buildGradle << """
                allprojects {
                    loom.mixin.useLegacyMixinAp = false
                }
                """.stripIndent()

		when:
		def result = gradle.run(task: "build")

		then:
		result.task(":build").outcome == SUCCESS
		!result.output.contains("[WARN]  [MIXIN]") // Assert that tiny remapper didnt not have any warnings when remapping
		gradle.getOutputZipEntry("leaf-example-mod-1.0.0.jar", "META-INF/MANIFEST.MF").contains("Leaf-Loom-Version: 0.0.0+unknown")
	}
}

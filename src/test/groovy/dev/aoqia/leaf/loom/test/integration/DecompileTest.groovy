/*
 * This file is part of leaf-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016-2022 aoqia, FabricMC
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

import spock.lang.Specification
import spock.lang.Unroll

import spock.lang.Specification
import spock.lang.Unroll

import dev.aoqia.leaf.loom.test.util.GradleProjectTestTrait

import spock.lang.Specification
import spock.lang.Unroll

import static dev.aoqia.loom.test.LoomTestConstants.*
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class DecompileTest extends Specification implements GradleProjectTestTrait {
	@Unroll
	def "#decompiler gradle #version"() {
		setup:
		def gradle = gradleProject(project: "decompile", version: version)

		when:
		def result = gradle.run(task: task)

		then:
		result.task(":${task}").outcome == SUCCESS

		where:
		decompiler 		| task								| version
		'fernflower'	| "genSourcesWithFernFlower"		| PRE_RELEASE_GRADLE
		'cfr' 			| "genSourcesWithCfr"				| PRE_RELEASE_GRADLE
		'vineflower' 	| "genSourcesWithVineflower"		| PRE_RELEASE_GRADLE
	}

	@Unroll
	def "custom decompiler (gradle #version)"() {
		setup:
		def gradle = gradleProject(project: "minimalBase", version: version)
		gradle.buildSrc("decompile")
		gradle.buildGradle << '''
                dependencies {
                    zomboid "com.theindiestone:zomboid:41.78.16"
                    mappings "dev.aoqia:leaf-yarn:0.1.0+build.1:v2"
                }
            '''
		when:
		def result = gradle.run(task: "genSourcesWithCustom")

		then:
		result.task(":genSourcesWithCustom").outcome == SUCCESS
		result.task(":preDecompile").outcome == SUCCESS
		result.output.contains("Writing test file")
		result.output.contains("Running custom decompiler")

		where:
		version << STANDARD_TEST_VERSIONS
	}

	def "decompile cache"() {
		setup:
		def gradle = gradleProject(project: "minimalBase", version: PRE_RELEASE_GRADLE, gradleHomeDir: File.createTempDir())
		gradle.buildSrc("decompile")
		gradle.buildGradle << '''
                dependencies {
                    zomboid "com.theindiestone:zomboid:41.78.16"
                    mappings "dev.aoqia:leaf-yarn:0.1.0+build.1:v2"
                }
        '''

		when:
		def result = gradle.run(tasks: ["genSourcesWithVineflower"], args: ["--use-cache", "--info"])

		// Add fabric API to the project, this introduces some transitive access wideners
		gradle.buildGradle << '''
                dependencies {
                    modImplementation "dev.aoqia:leaf-api:0.1.0+41.78.16"
                }
        '''

		def result2 = gradle.run(tasks: ["genSourcesWithVineflower"], args: ["--use-cache", "--info"])

		// And run again, with no changes
		def result3 = gradle.run(tasks: ["genSourcesWithVineflower"], args: ["--use-cache", "--info"])

		then:
		result.task(":genSourcesWithVineflower").outcome == SUCCESS
		result2.task(":genSourcesWithVineflower").outcome == SUCCESS
		result3.task(":genSourcesWithVineflower").outcome == SUCCESS
	}
}

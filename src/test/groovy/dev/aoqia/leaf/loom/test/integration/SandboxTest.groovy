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
package dev.aoqia.leaf.loom.test.integration

import java.nio.file.Path

import dev.aoqia.loom.test.util.GradleProjectTestTrait
import dev.aoqia.loom.test.util.ZipTestUtils
import org.intellij.lang.annotations.Language
import spock.lang.Specification
import spock.lang.Unroll

import dev.aoqia.loom.test.util.GradleProjectTestTrait
import dev.aoqia.loom.test.util.ZipTestUtils
import org.intellij.lang.annotations.Language
import spock.lang.Specification
import spock.lang.Unroll

import dev.aoqia.leaf.loom.configuration.mods.dependency.LocalMavenHelper
import dev.aoqia.leaf.loom.test.unit.sandbox.SandboxEntrypoint
import dev.aoqia.leaf.loom.util.Constants
import dev.aoqia.leaf.loom.util.ZipUtils

import dev.aoqia.loom.test.util.GradleProjectTestTrait
import dev.aoqia.loom.test.util.ZipTestUtils
import org.intellij.lang.annotations.Language
import spock.lang.Specification
import spock.lang.Unroll

import static dev.aoqia.loom.test.LoomTestConstants.STANDARD_TEST_VERSIONS
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class SandboxTest extends Specification implements GradleProjectTestTrait {
	@Unroll
	def "sandbox (gradle #version)"() {
		setup:
		def gradle = gradleProject(project: "minimalBase", version: version)
		gradle.buildGradle << '''
                repositories {
                    mavenLocal()
                }

                dependencies {
                    zomboid "com.theindiestone:zomboid:41.78.16"
                    mappings "dev.aoqia:leaf-yarn:0.1.0+build.1:v2"
                    modImplementation "dev.aoqia:leaf-loader:0.1.0"
                }
            '''
		new File(gradle.getProjectDir(), "gradle.properties").text = "${Constants.Properties.SANDBOX}=dev.aoqia.loom.test:sandbox:1.0.0"

		def mavenHelper = new LocalMavenHelper("dev.aoqia.loom.test", "sandbox", "1.0.0", null, gradle.getMavenLocalDir().toPath())
		mavenHelper.copyToMaven(createDummySandboxJar(), null)
		mavenHelper.savePom()

		when:
		def result = gradle.run(task: "runClientSandbox")

		then:
		result.task(":runClientSandbox").outcome == SUCCESS
		result.output.contains("Running real main: net.fabricmc.loader.impl.launch.knot.KnotClient")
		// Ensure that we weren't launched via DLI
		!result.output.contains("at net.fabricmc.devlaunchinjector.Main")

		where:
		version << STANDARD_TEST_VERSIONS
	}

	static Path createDummySandboxJar() {
		def zip = ZipTestUtils.createZip(["fabric-sandbox.json": METADATA_JSON], ".jar")
		ZipUtils.add(zip, "dev/aoqia/loom/test/unit/sandbox/SandboxEntrypoint.class", getClassBytes(SandboxEntrypoint.class))
		return zip
	}

	static byte[] getClassBytes(Class<?> clazz) {
		return clazz.classLoader.getResourceAsStream(clazz.name.replace('.', '/') + ".class").withCloseable {
			it.bytes
		}
	}

	// Ensure that all platforms that the test may run on are listed here
	@Language("json")
	private static String METADATA_JSON = """
        {
            "version": 1,
            "mainClass": "net.sandbox.unit.test.loom.aoqia.SandboxEntrypoint",
            "platforms": {
                "windows": [
                    "arm64",
                    "x86_64"
                ],
                "macos": [
                    "arm64",
                    "x86_64"
                ],
                "linux": [
                    "arm64",
                    "x86_64"
                ]
            }
        }
    """
}

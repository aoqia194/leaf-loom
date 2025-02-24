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

import java.nio.file.Path

import spock.lang.Specification

import spock.lang.Specification

import dev.aoqia.leaf.loom.configuration.mods.ArtifactMetadata
import dev.aoqia.leaf.loom.configuration.mods.ArtifactRef

import static dev.aoqia.leaf.loom.configuration.mods.ArtifactMetadata.MixinRemapType.MIXIN
import static dev.aoqia.leaf.loom.configuration.mods.ArtifactMetadata.MixinRemapType.STATIC
import static dev.aoqia.leaf.loom.configuration.mods.ArtifactMetadata.RemapRequirements.*
import static dev.aoqia.leaf.loom.test.util.ZipTestUtils.createZip
import static dev.aoqia.leaf.loom.test.util.ZipTestUtils.manifest

class ArtifactMetadataTest extends Specification {
	def "is leaf mod"() {
		given:
		def zip = createZip(entries)
		when:
		def metadata = createMetadata(zip)
		then:
		isMod == metadata.isLeafMod()
		where:
		isMod 		| entries
		false       | ["hello.json": "{}"] // None Mod jar
		true        | ["leaf.mod.json": "{}"] // Leaf mod
	}

	def "remap requirements"() {
		given:
		def zip = createZip(entries)
		when:
		def metadata = createMetadata(zip)
		then:
		requirements == metadata.remapRequirements()
		where:
		requirements | entries
		DEFAULT      | ["leaf.mod.json": "{}"] 										// Default
		OPT_OUT      | ["META-INF/MANIFEST.MF": manifest("Leaf-Loom-Remap", "false")] // opt-out
		OPT_IN       | ["META-INF/MANIFEST.MF": manifest("Leaf-Loom-Remap", "true")]	// opt-in
	}

	def "Should Remap" () {
		given:
		def zip = createZip(entries)
		when:
		def metadata = createMetadata(zip)
		def result = metadata.shouldRemap()
		then:
		result == shouldRemap
		where:
		shouldRemap | entries
		false       | ["hello.json": "{}"] // None Mod jar
		true        | ["leaf.mod.json": "{}"] // Leaf mod
		false       | ["leaf.mod.json": "{}",
			"META-INF/MANIFEST.MF": manifest("Leaf-Loom-Remap", "false")] 	// Fabric mod opt-out
		true        | ["leaf.mod.json": "{}",
			"META-INF/MANIFEST.MF": manifest("Leaf-Loom-Remap", "true")]	// Fabric mod opt-in
		false       | ["hello.json": "{}",
			"META-INF/MANIFEST.MF": manifest("Leaf-Loom-Remap", "false")]	// None opt-out
		true        | ["hello.json": "{}",
			"META-INF/MANIFEST.MF": manifest("Leaf-Loom-Remap", "true")]	// None opt-int
		false        | ["hello.json": "{}",
			"META-INF/MANIFEST.MF": manifest("Leaf-Loom-Remap", "broken")]// Invalid format
		false        | ["hello.json": "{}",
			"META-INF/MANIFEST.MF": manifest("Something", "Hello")]			// Invalid format
	}

	def "Installer data"() {
		given:
		def zip = createZip(entries)
		when:
		def metadata = createMetadata(zip)
		then:
		isLoader == (metadata.installerData() != null)
		where:
		isLoader   | entries
		true       | ["leaf.mod.json": "{}", "leaf-installer.json": "{}"] // Fabric mod, with installer data
		false      | ["leaf.mod.json": "{}"] // Fabric mod, no installer data
	}

	def "Refmap remap type" () {
		given:
		def zip = createZip(entries)
		when:
		def metadata = createMetadata(zip)
		def result = metadata.mixinRemapType()
		then:
		result == type
		where:
		type | entries
		MIXIN       | ["hello.json": "{}"]       // None Mod jar
		MIXIN       | ["leaf.mod.json": "{}"]  // Fabric mod without manfiest file
		MIXIN       | ["leaf.mod.json": "{}", "META-INF/MANIFEST.MF": manifest("Leaf-Loom-Remap", "true")]              // Fabric mod without remap type entry
		MIXIN       | ["leaf.mod.json": "{}", "META-INF/MANIFEST.MF": manifest("Leaf-Loom-Mixin-Remap-Type", "mixin")]  // Fabric mod with remap type entry "mixin"
		STATIC      | ["leaf.mod.json": "{}", "META-INF/MANIFEST.MF": manifest("Leaf-Loom-Mixin-Remap-Type", "static")] // Fabric mod with remap type entry "static"
	}

	// Test that a mod with the same or older version of loom can be read
	def "Valid loom version"() {
		given:
		def zip = createModWithRemapType(modLoomVersion, "static")
		when:
		def metadata = createMetadata(zip, loomVersion)
		then:
		metadata != null
		where:
		loomVersion | modLoomVersion
		"1.4"       | "1.4"
		"1.4"       | "1.4.0"
		"1.4"       | "1.4.1"
		"1.4"       | "1.4.99"
		"1.4"       | "1.4.local"
		"1.5"       | "1.4.99"
		"2.0"       | "1.4.99"
	}

	// Test that a mod with the same or older version of loom can be read
	def "Invalid loom version"() {
		given:
		def zip = createModWithRemapType(modLoomVersion, "static")
		when:
		def metadata = createMetadata(zip, loomVersion)
		then:
		def e = thrown(IllegalStateException)
		e.message == "Mod was built with a newer version of Loom ($modLoomVersion), you are using Loom ($loomVersion)"
		where:
		loomVersion | modLoomVersion
		"1.4"       | "1.5"
		"1.4"       | "1.5.00"
		"1.4"       | "2.0"
		"1.4"       | "2.4"
	}

	def "Accepts all Loom versions for remap 'false'"() {
		given:
		def zip = createModWithRemap(modLoomVersion, false)
		when:
		def metadata = createMetadata(zip, loomVersion)
		then:
		metadata != null
		where:
		loomVersion | modLoomVersion
		// Valid
		"1.4"       | "1.0.1"
		"1.4"       | "1.0.99"
		"1.4"       | "1.4"
		"1.4"       | "1.4.0"
		"1.4"       | "1.4.1"
		"1.4"       | "1.4.99"
		"1.4"       | "1.4.local"
		"1.5"       | "1.4.99"
		"2.0"       | "1.4.99"
		// Usually invalid
		"1.4"       | "1.5"
		"1.4"       | "1.5.00"
		"1.4"       | "2.0"
		"1.4"       | "2.4"
	}

	def "Accepts all Loom versions for remap 'true'"() {
		given:
		def zip = createModWithRemap(modLoomVersion, true)
		when:
		def metadata = createMetadata(zip, loomVersion)
		then:
		metadata != null
		where:
		loomVersion | modLoomVersion
		// Valid
		"1.4"       | "1.0.1"
		"1.4"       | "1.0.99"
		"1.4"       | "1.4"
		"1.4"       | "1.4.0"
		"1.4"       | "1.4.1"
		"1.4"       | "1.4.99"
		"1.4"       | "1.4.local"
		"1.5"       | "1.4.99"
		"2.0"       | "1.4.99"
		// Usually invalid
		"1.4"       | "1.5"
		"1.4"       | "1.5.00"
		"1.4"       | "2.0"
		"1.4"       | "2.4"
	}

	def "known indy BSMs"() {
		given:
		def zip = createZip(entries)
		when:
		def metadata = createMetadata(zip)
		then:
		knownBSMs == metadata.knownIdyBsms()
		where:
		knownBSMs | entries
		[]                    | ["leaf.mod.json": "{}"] // Default
		["com/example/Class"] | ["META-INF/MANIFEST.MF": manifest("Leaf-Loom-Known-Indy-BSMS", "com/example/Class")] // single bsm
		[
			"com/example/Class",
			"com/example/Another"
		] | ["META-INF/MANIFEST.MF": manifest("Leaf-Loom-Known-Indy-BSMS", "com/example/Class,com/example/Another")] // two bsms
	}

	private static Path createModWithRemapType(String loomVersion, String remapType) {
		return createZip(["leaf.mod.json": "{}", "META-INF/MANIFEST.MF": manifest(["Leaf-Loom-Version": loomVersion, "Leaf-Loom-Mixin-Remap-Type": remapType])])
	}

	private static Path createModWithRemap(String loomVersion, boolean remap) {
		return createZip(["leaf.mod.json": "{}", "META-INF/MANIFEST.MF": manifest(["Leaf-Loom-Version": loomVersion, "Leaf-Loom-Mixin-Remap": remap ? "true" : "false"])])
	}

	private static ArtifactMetadata createMetadata(Path zip, String loomVersion = "1.4") {
		return ArtifactMetadata.create(createArtifact(zip), loomVersion)
	}

	private static ArtifactRef createArtifact(Path zip) {
		return new ArtifactRef.FileArtifactRef(zip, "dev.aoqia", "loom-test", "1.0")
	}
}

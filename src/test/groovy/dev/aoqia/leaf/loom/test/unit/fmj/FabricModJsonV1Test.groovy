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
package dev.aoqia.leaf.loom.test.unit.fmj


import com.google.gson.Gson
import com.google.gson.JsonObject
import org.intellij.lang.annotations.Language
import spock.lang.Specification

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.intellij.lang.annotations.Language
import spock.lang.Specification

import dev.aoqia.leaf.loom.util.Constants
import dev.aoqia.leaf.loom.util.fmj.LeafModJsonFactory
import dev.aoqia.leaf.loom.util.fmj.LeafModJsonSource
import dev.aoqia.leaf.loom.util.fmj.ModEnvironment

class LeafModJsonV1Test extends Specification {
	@Language("json")
	static String JSON = """
{
    "schemaVersion": 1,
    "id": "example-mod-id",
    "name": "Example mod name for testing",
    "version": "1.0.0",
    "environment": "client",
    "license": "Apache-2.0",
    "mixins": [
        {
          "config": "test.client.mixins.json",
          "environment": "client"
        },
        "test.mixins.json"
    ],
    "accessWidener" : "modid.accesswidener",
    "custom": {
        "loom:injected_interfaces": {
          "net/minecraft/class_123": ["net/test/TestClass"]
        }
    }
}
"""

	static JsonObject JSON_OBJECT = new Gson().fromJson(JSON, JsonObject.class)

	def "version"() {
		given:
		def mockSource = Mock(LeafModJsonSource)
		when:
		def fmj = LeafModJsonFactory.create(JSON_OBJECT, mockSource)
		then:
		fmj.version == 1
		fmj.modVersion == "1.0.0"
	}

	def "id"() {
		given:
		def mockSource = Mock(LeafModJsonSource)
		when:
		def fmj = LeafModJsonFactory.create(JSON_OBJECT, mockSource)
		then:
		fmj.id == "example-mod-id"
	}

	def "mixins"() {
		given:
		def mockSource = Mock(LeafModJsonSource)
		when:
		def fmj = LeafModJsonFactory.create(JSON_OBJECT, mockSource)
		then:
		fmj.mixinConfigurations == [
			"test.client.mixins.json",
			"test.mixins.json"
		]
	}

	def "injected interfaces"() {
		given:
		def mockSource = Mock(LeafModJsonSource)
		when:
		def fmj = LeafModJsonFactory.create(JSON_OBJECT, mockSource)
		def jsonObject = fmj.getCustom(Constants.CustomModJsonKeys.INJECTED_INTERFACE)
		then:
		jsonObject instanceof JsonObject
		jsonObject.has("net/minecraft/class_123")
	}

	def "access widener"() {
		given:
		def mockSource = Mock(LeafModJsonSource)
		when:
		def fmj = LeafModJsonFactory.create(JSON_OBJECT, mockSource)
		then:
		fmj.getClassTweakers() == ["modid.accesswidener": ModEnvironment.UNIVERSAL]
	}

	def "hash code"() {
		given:
		def mockSource = Mock(LeafModJsonSource)
		when:
		def fmj = LeafModJsonFactory.create(JSON_OBJECT, mockSource)
		then:
		fmj.hashCode() == 930565977
	}
}

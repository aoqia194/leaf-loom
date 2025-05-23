/*
 * This file is part of leaf-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2023 aoqia, FabricMC
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
package dev.aoqia.leaf.loom.test.unit.processor


import spock.lang.Specification

import spock.lang.Specification

import dev.aoqia.leaf.loom.configuration.accesswidener.ModAccessWidenerEntry
import dev.aoqia.leaf.loom.util.fmj.ModEnvironment

import spock.lang.Specification

class ModAccessWidenerEntryTest extends Specification {
	def "read local mod"() {
		given:
		def mod = Mock(LeafModJson.Mockable)
		mod.getClassTweakers() >> ["test.accesswidener": ModEnvironment.UNIVERSAL]
		mod.hashCode() >> 0

		when:
		def entries = ModAccessWidenerEntry.readAll(mod, true)
		then:
		entries.size() == 1
		def entry = entries[0]

		entry.path() == "test.accesswidener"
		entry.environment() == ModEnvironment.UNIVERSAL
		entry.transitiveOnly()
		entry.hashCode() == -1218981396
	}
}

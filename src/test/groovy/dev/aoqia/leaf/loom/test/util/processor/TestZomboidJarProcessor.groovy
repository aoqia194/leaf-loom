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
package dev.aoqia.leaf.loom.test.util.processor

import java.nio.file.Path

import groovy.transform.Immutable

import dev.aoqia.leaf.loom.api.processor.ProcessorContext
import dev.aoqia.leaf.loom.api.processor.SpecContext
import dev.aoqia.leaf.loom.api.processor.ZomboidJarProcessor

@Immutable
class TestZomboidJarProcessor implements ZomboidJarProcessor<Spec> {
	String input

	final String name = "TestProcessor"

	@Override
	Spec buildSpec(SpecContext context) {
		if (input == null) {
			return null
		}

		return new Spec(input)
	}

	@Immutable
	class Spec implements ZomboidJarProcessor.Spec {
		String input
	}

	@Override
	void processJar(Path jar, Spec spec, ProcessorContext context) throws IOException {
	}
}

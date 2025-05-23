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
package dev.aoqia.leaf.loom.test.integration.buildSrc.remapext

import javax.inject.Inject

import org.gradle.api.provider.MapProperty
import org.objectweb.asm.ClassVisitor

import org.gradle.api.provider.MapProperty
import org.objectweb.asm.ClassVisitor

import dev.aoqia.leaf.loom.api.remapping.RemapperContext
import dev.aoqia.leaf.loom.api.remapping.RemapperExtension
import dev.aoqia.leaf.loom.api.remapping.RemapperParameters
import dev.aoqia.leaf.loom.util.Constants

import org.gradle.api.provider.MapProperty
import org.objectweb.asm.ClassVisitor

class TestRemapperExtension implements RemapperExtension<Params> {
	final Params parameters

	@Inject
	TestRemapperExtension(Params parameters) {
		this.parameters = parameters
	}

	@Override
	ClassVisitor insertVisitor(String className, RemapperContext remapperContext, ClassVisitor classVisitor) {
		def replacements = parameters.replacements.get()
		return new StringReplacementClassVisitor(Constants.ASM_VERSION, classVisitor, replacements)
	}

	interface Params extends RemapperParameters {
		MapProperty<String, String> getReplacements()
	}
}

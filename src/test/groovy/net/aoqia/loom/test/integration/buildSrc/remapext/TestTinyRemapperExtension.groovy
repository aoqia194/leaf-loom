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
package net.aoqia.loom.test.integration.buildSrc.remapext

import org.objectweb.asm.ClassVisitor

import net.fabricmc.tinyremapper.TinyRemapper

import org.objectweb.asm.ClassVisitor

import net.aoqia.loom.api.remapping.RemapperContext
import net.aoqia.loom.api.remapping.RemapperExtension
import net.aoqia.loom.api.remapping.RemapperParameters
import net.aoqia.loom.api.remapping.TinyRemapperExtension

class TestTinyRemapperExtension implements RemapperExtension<RemapperParameters.None>, TinyRemapperExtension {
	@Override
	ClassVisitor insertVisitor(String className, RemapperContext remapperContext, ClassVisitor classVisitor) {
		return classVisitor
	}

	TinyRemapper.AnalyzeVisitorProvider analyzeVisitorProvider = null
	TinyRemapper.ApplyVisitorProvider preApplyVisitor = null
	TinyRemapper.ApplyVisitorProvider PostApplyVisitor = null
}

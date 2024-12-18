/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2024 FabricMC
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

package net.aoqia.loom.test.unit.service

import org.gradle.api.provider.Property
import spock.lang.Specification

import org.gradle.api.provider.Property
import spock.lang.Specification

import net.aoqia.loom.test.util.GradleTestUtil
import net.aoqia.loom.util.service.ScopedServiceFactory
import net.aoqia.loom.util.service.Service
import net.aoqia.loom.util.service.ServiceType

abstract class ServiceTestBase extends Specification {
	ScopedServiceFactory factory

	def setup() {
		factory = new ScopedServiceFactory()
	}

	def cleanup() {
		factory.close()
		factory = null
	}

	static Property<String> serviceClassProperty(ServiceType<? extends Service.Options, ? extends Service> type) {
		return GradleTestUtil.mockProperty(type.serviceClass().name)
	}
}

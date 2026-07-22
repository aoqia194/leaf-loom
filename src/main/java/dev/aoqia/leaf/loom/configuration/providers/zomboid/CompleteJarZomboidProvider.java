/*
 * This file is part of leaf-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2025 aoqia194, FabricMC
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

package dev.aoqia.leaf.loom.configuration.providers.zomboid;

import java.nio.file.Path;
import java.util.List;

import dev.aoqia.leaf.loom.api.mappings.layered.MappingsNamespace;
import dev.aoqia.leaf.loom.configuration.ConfigContext;

public final class CompleteJarZomboidProvider extends ZomboidProvider {
	private Path zomboidJar;

	public CompleteJarZomboidProvider(ZomboidMetadataProvider metadataProvider, ConfigContext configContext) {
		super(metadataProvider, configContext);

        if (isLegacyVersion()) {
            throw new RuntimeException("Complete JAR provider not supported for legacy PZ versions.");
        }
	}

	@Override
	protected void initFiles() {
		super.initFiles();

		zomboidJar = path("projectzomboid.jar");
	}

	@Override
	public List<Path> getZomboidJars() {
		return List.of(zomboidJar);
	}

    @Override
    public MappingsNamespace getOfficialNamespace() {
        return MappingsNamespace.OFFICIAL;
    }

	@Override
	public void provide() throws Exception {
		super.provide();

        if (!provideClient() || !provideServer()) {
            throw new UnsupportedOperationException("This provider only provides both the client and server!");
        }
	}

	public Path getZomboidJar() {
		return zomboidJar;
	}
}

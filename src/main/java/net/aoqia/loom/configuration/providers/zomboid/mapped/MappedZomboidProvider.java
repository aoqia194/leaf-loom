/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021 FabricMC
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

package net.aoqia.loom.configuration.providers.zomboid.mapped;

import java.nio.file.Path;
import java.util.List;
import net.aoqia.loom.configuration.providers.zomboid.ZomboidJar;
import net.aoqia.loom.configuration.providers.zomboid.SingleJarEnvType;

public interface MappedZomboidProvider {
    default List<Path> getZomboidJarPaths() {
        return getMinecraftJars().stream().map(ZomboidJar::getPath).toList();
    }

    List<ZomboidJar> getMinecraftJars();

    interface ProviderImpl extends MappedZomboidProvider {
        Path getJar(ZomboidJar.Type type);
    }

    interface Merged extends ProviderImpl {
        default ZomboidJar getMergedJar() {
            return new ZomboidJar.Merged(getJar(ZomboidJar.Type.MERGED));
        }

        @Override
        default List<ZomboidJar> getMinecraftJars() {
            return List.of(getMergedJar());
        }
    }

    interface Split extends ProviderImpl {
        default ZomboidJar getCommonJar() {
            return new ZomboidJar.Common(getJar(ZomboidJar.Type.COMMON));
        }

        default ZomboidJar getClientOnlyJar() {
            return new ZomboidJar.ClientOnly(getJar(ZomboidJar.Type.CLIENT_ONLY));
        }

        @Override
        default List<ZomboidJar> getMinecraftJars() {
            return List.of(getCommonJar(), getClientOnlyJar());
        }
    }

    interface SingleJar extends ProviderImpl {
        SingleJarEnvType env();

        default ZomboidJar.Type envType() {
            return env().getType();
        }

        default ZomboidJar getEnvOnlyJar() {
            return env().getJar().apply(getJar(env().getType()));
        }

        @Override
        default List<ZomboidJar> getMinecraftJars() {
            return List.of(getEnvOnlyJar());
        }
    }
}

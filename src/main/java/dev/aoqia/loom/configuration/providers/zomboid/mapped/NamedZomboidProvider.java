/*
 * This file is part of leaf-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2021-2022 aoqia, FabricMC
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
package dev.aoqia.loom.configuration.providers.zomboid.mapped;

import java.util.List;
import dev.aoqia.loom.api.mappings.layered.MappingsNamespace;
import dev.aoqia.loom.configuration.providers.zomboid.*;
import net.fabricmc.tinyremapper.TinyRemapper;
import org.gradle.api.Project;

public abstract class NamedZomboidProvider<M extends ZomboidProvider> extends AbstractMappedZomboidProvider<M> {
    public NamedZomboidProvider(Project project, M minecraftProvider) {
        super(project, minecraftProvider);
    }

    @Override
    public final MappingsNamespace getTargetNamespace() {
        return MappingsNamespace.NAMED;
    }

    @Override
    public MavenScope getMavenScope() {
        return MavenScope.GLOBAL;
    }

    public static final class MergedImpl extends NamedZomboidProvider<MergedZomboidProvider> implements Merged {
        public MergedImpl(Project project, MergedZomboidProvider minecraftProvider) {
            super(project, minecraftProvider);
        }

        @Override
        public List<RemappedJars> getRemappedJars() {
            return List.of(new RemappedJars(
                    zomboidProvider.getMergedJar(), getMergedJar(), zomboidProvider.getOfficialNamespace()));
        }

        @Override
        public List<ZomboidJar.Type> getDependencyTypes() {
            return List.of(ZomboidJar.Type.MERGED);
        }
    }

    public static final class SplitImpl extends NamedZomboidProvider<SplitZomboidProvider> implements Split {
        public SplitImpl(Project project, SplitZomboidProvider zomboidProvider) {
            super(project, zomboidProvider);
        }

        @Override
        public List<RemappedJars> getRemappedJars() {
            return List.of(
                    new RemappedJars(
                            zomboidProvider.getCommonJar(), getCommonJar(), zomboidProvider.getOfficialNamespace()),
                    new RemappedJars(
                            zomboidProvider.getClientOnlyJar(),
                            getClientOnlyJar(),
                            zomboidProvider.getOfficialNamespace(),
                            zomboidProvider.getCommonJar()));
        }

        @Override
        protected void configureRemapper(RemappedJars remappedJars, TinyRemapper.Builder tinyRemapperBuilder) {
            configureSplitRemapper(remappedJars, tinyRemapperBuilder);
        }

        @Override
        public List<ZomboidJar.Type> getDependencyTypes() {
            return List.of(ZomboidJar.Type.CLIENT_ONLY, ZomboidJar.Type.COMMON);
        }
    }

    public static final class SingleJarImpl extends NamedZomboidProvider<SingleJarZomboidProvider>
            implements SingleJar {
        private final SingleJarEnvType env;

        private SingleJarImpl(Project project, SingleJarZomboidProvider minecraftProvider, SingleJarEnvType env) {
            super(project, minecraftProvider);
            this.env = env;
        }

        public static SingleJarImpl server(Project project, SingleJarZomboidProvider minecraftProvider) {
            return new SingleJarImpl(project, minecraftProvider, SingleJarEnvType.SERVER);
        }

        public static SingleJarImpl client(Project project, SingleJarZomboidProvider minecraftProvider) {
            return new SingleJarImpl(project, minecraftProvider, SingleJarEnvType.CLIENT);
        }

        @Override
        public List<RemappedJars> getRemappedJars() {
            return List.of(new RemappedJars(
                    zomboidProvider.getZomboidEnvOnlyJar(), getEnvOnlyJar(), zomboidProvider.getOfficialNamespace()));
        }

        @Override
        public List<ZomboidJar.Type> getDependencyTypes() {
            return List.of(envType());
        }

        @Override
        public SingleJarEnvType env() {
            return env;
        }
    }
}

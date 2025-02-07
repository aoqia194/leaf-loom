/*
 * This file is part of leaf-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2024 aoqia, FabricMC
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
package dev.aoqia.loom.task.service;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import dev.aoqia.loom.LoomGradleExtension;
import dev.aoqia.loom.extension.MixinExtension;
import dev.aoqia.loom.task.RemapJarTask;
import dev.aoqia.loom.util.ZipUtils;
import dev.aoqia.loom.util.fmj.LeafModJson;
import dev.aoqia.loom.util.fmj.LeafModJsonFactory;
import dev.aoqia.loom.util.service.Service;
import dev.aoqia.loom.util.service.ServiceFactory;
import dev.aoqia.loom.util.service.ServiceType;
import org.gradle.api.Project;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.SourceSet;

public class MixinRefmapService extends Service<MixinRefmapService.Options> {
    public static final ServiceType<Options, MixinRefmapService> TYPE =
            new ServiceType<>(Options.class, MixinRefmapService.class);

    public interface Options extends Service.Options {
        @Input
        ListProperty<String> getMixinConfigs();

        @Input
        Property<String> getRefmapName();
    }

    public static Provider<List<Options>> createOptions(RemapJarTask task) {
        final Project project = task.getProject();
        return project.provider(() -> {
            final LoomGradleExtension extension = LoomGradleExtension.get(project);

            if (!extension.getMixin().getUseLegacyMixinAp().get()) {
                return List.of();
            }

            final MixinExtension mixinExtension = extension.getMixin();

            List<Provider<Options>> options = new ArrayList<>();

            for (SourceSet sourceSet : mixinExtension.getMixinSourceSets()) {
                MixinExtension.MixinInformationContainer container =
                        Objects.requireNonNull(MixinExtension.getMixinInformationContainer(sourceSet));

                final List<String> rootPaths = ClientEntriesService.getRootPaths(
                        sourceSet.getResources().getSrcDirs());

                final String refmapName = container.refmapNameProvider().get();
                final List<String> mixinConfigs =
                        container
                                .sourceSet()
                                .getResources()
                                .matching(container.mixinConfigPattern())
                                .getFiles()
                                .stream()
                                .map(ClientEntriesService.relativePath(rootPaths))
                                .toList();

                options.add(createOptions(project, mixinConfigs, refmapName));
            }

            return options.stream().map(Provider::get).toList();
        });
    }

    private static Provider<Options> createOptions(Project project, List<String> mixinConfigs, String refmapName) {
        return TYPE.create(project, o -> {
            o.getMixinConfigs().set(mixinConfigs);
            o.getRefmapName().set(refmapName);
        });
    }

    public MixinRefmapService(Options options, ServiceFactory serviceFactory) {
        super(options, serviceFactory);
    }

    public void applyToJar(Path path) throws IOException {
        final LeafModJson leafModJson = LeafModJsonFactory.createFromZipNullable(path);

        if (leafModJson == null) {
            return;
        }

        final List<String> allMixinConfigs = leafModJson.getMixinConfigurations();
        final List<String> mixinConfigs = getOptions().getMixinConfigs().get().stream()
                .filter(allMixinConfigs::contains)
                .toList();
        final String refmapName = getOptions().getRefmapName().get();

        if (ZipUtils.contains(path, refmapName)) {
            int transformed = ZipUtils.transformJson(
                    JsonObject.class, path, mixinConfigs.stream().collect(Collectors.toMap(s -> s, s -> json -> {
                        if (!json.has("refmap")) {
                            json.addProperty("refmap", refmapName);
                        }

                        return json;
                    })));
        }
    }
}

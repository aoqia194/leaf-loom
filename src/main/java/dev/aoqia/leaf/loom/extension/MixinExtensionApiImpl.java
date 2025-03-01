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
package dev.aoqia.leaf.loom.extension;

import java.util.Objects;
import dev.aoqia.leaf.loom.api.MixinExtensionAPI;
import dev.aoqia.leaf.loom.api.mappings.layered.MappingsNamespace;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.util.PatternSet;

public abstract class MixinExtensionApiImpl implements MixinExtensionAPI {
    protected final Project project;
    protected final Property<Boolean> useMixinAp;
    private final Property<String> refmapTargetNamespace;
    private final MapProperty<String, String> messages;
    private final Property<Boolean> showMessageTypes;

    public MixinExtensionApiImpl(Project project) {
        this.project = Objects.requireNonNull(project);
        this.useMixinAp = project.getObjects().property(Boolean.class).convention(false);

        this.refmapTargetNamespace =
                project.getObjects().property(String.class).convention(MappingsNamespace.OFFICIAL.toString());
        this.refmapTargetNamespace.finalizeValueOnRead();

        this.messages = project.getObjects().mapProperty(String.class, String.class);
        this.messages.finalizeValueOnRead();

        this.showMessageTypes = project.getObjects().property(Boolean.class);
        this.showMessageTypes.convention(false).finalizeValueOnRead();
    }

    protected final PatternSet add0(SourceSet sourceSet, String refmapName) {
        return add0(sourceSet, project.provider(() -> refmapName));
    }

    protected abstract PatternSet add0(SourceSet sourceSet, Provider<String> refmapName);

    @Override
    public Property<Boolean> getUseLegacyMixinAp() {
        return useMixinAp;
    }

    @Override
    public Property<String> getRefmapTargetNamespace() {
        if (!getUseLegacyMixinAp().get())
            throw new IllegalStateException(
                    "You need to set useLegacyMixinAp = true to configure Mixin annotation processor.");

        return refmapTargetNamespace;
    }

    @Override
    public void add(SourceSet sourceSet, String refmapName, Action<PatternSet> action) {
        PatternSet pattern = add0(sourceSet, refmapName);
        action.execute(pattern);
    }

    @Override
    public void add(SourceSet sourceSet, String refmapName) {
        add(sourceSet, refmapName, x -> {});
    }

    @Override
    public void add(String sourceSetName, String refmapName, Action<PatternSet> action) {
        add(sourceSetName, project.provider(() -> refmapName), action);
    }

    public void add(String sourceSetName, Provider<String> refmapName, Action<PatternSet> action) {
        add(resolveSourceSet(sourceSetName), refmapName, action);
    }

    public void add(SourceSet sourceSet, Provider<String> refmapName, Action<PatternSet> action) {
        PatternSet pattern = add0(sourceSet, refmapName);
        action.execute(pattern);
    }

    @Override
    public void add(String sourceSetName, String refmapName) {
        add(sourceSetName, refmapName, x -> {});
    }

    @Override
    public void add(SourceSet sourceSet, Action<PatternSet> action) {
        add(sourceSet, getDefaultRefmapName(), action);
    }

    @Override
    public void add(SourceSet sourceSet) {
        add(sourceSet, x -> {});
    }

    @Override
    public void add(String sourceSetName, Action<PatternSet> action) {
        add(sourceSetName, getDefaultRefmapName(), action);
    }

    @Override
    public void add(String sourceSetName) {
        add(sourceSetName, x -> {});
    }

    @Override
    public MapProperty<String, String> getMessages() {
        return messages;
    }

    @Override
    public Property<Boolean> getShowMessageTypes() {
        return showMessageTypes;
    }

    @Override
    public void messages(Action<MapProperty<String, String>> action) {
        action.execute(messages);
    }

    private SourceSet resolveSourceSet(String sourceSetName) {
        // try to find sourceSet with name sourceSetName in this project
        SourceSet sourceSet = project.getExtensions()
                .getByType(JavaPluginExtension.class)
                .getSourceSets()
                .findByName(sourceSetName);

        if (sourceSet == null) {
            throw new InvalidUserDataException("No sourceSet " + sourceSetName + " was found");
        }

        return sourceSet;
    }

    // This is here to ensure that LoomGradleExtensionApiImpl compiles without any unimplemented methods
    private final class EnsureCompile extends MixinExtensionApiImpl {
        private EnsureCompile() {
            super(null);
            throw new RuntimeException();
        }

        @Override
        public Property<String> getDefaultRefmapName() {
            throw new RuntimeException("Yeah... something is really wrong");
        }

        @Override
        protected PatternSet add0(SourceSet sourceSet, Provider<String> refmapName) {
            throw new RuntimeException("Yeah... something is really wrong");
        }
    }
}

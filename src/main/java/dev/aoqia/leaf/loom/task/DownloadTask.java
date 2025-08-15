package dev.aoqia.leaf.loom.task;

import java.net.URISyntaxException;
import java.time.Duration;

import dev.aoqia.leaf.loom.util.ExceptionUtil;
import dev.aoqia.leaf.loom.util.download.Download;
import dev.aoqia.leaf.loom.util.download.DownloadBuilder;
import dev.aoqia.leaf.loom.util.download.DownloadException;

import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;
import org.jetbrains.annotations.ApiStatus;

/**
 * A general purpose task for downloading files from a URL, using the loom {@link Download}
 * utility.
 */
public abstract class DownloadTask extends DefaultTask {
    @Inject
    public DownloadTask() {
        getIsOffline().set(getProject().getGradle().getStartParameter().isOffline());
    }

    /**
     * The URL to download the file from.
     */
    @Input
    public abstract Property<String> getUrl();

    /**
     * The expected SHA-1 hash of the downloaded file.
     */
    @Optional
    @Input
    public abstract Property<String> getSha1();

    /**
     * The maximum age of the downloaded file in days. When not provided the downloaded file will
     * never be considered stale.
     */
    @Optional
    @Input
    public abstract Property<Duration> getMaxAge();

    // Internal stuff:

    /**
     * The file to download to.
     */
    @OutputFile
    public abstract RegularFileProperty getOutput();

    @ApiStatus.Internal
    @Input
    protected abstract Property<Boolean> getIsOffline();

    @Inject
    protected abstract WorkerExecutor getWorkerExecutor();

    @TaskAction
    public void run() {
        final WorkQueue workQueue = getWorkerExecutor().noIsolation();

        workQueue.submit(DownloadAction.class, params -> {
            params.getUrl().set(getUrl());
            params.getSha1().set(getSha1());
            params.getMaxAge().set(getMaxAge());
            params.getOutputFile().set(getOutput());
            params.getIsOffline().set(getIsOffline());
        });
    }

    public interface DownloadWorkParameters extends WorkParameters {
        Property<String> getUrl();

        Property<String> getSha1();

        Property<Duration> getMaxAge();

        RegularFileProperty getOutputFile();

        Property<Boolean> getIsOffline();
    }

    public abstract static class DownloadAction implements WorkAction<DownloadWorkParameters> {
        @Override
        public void execute() {
            DownloadBuilder builder;

            try {
                builder = Download.create(getParameters().getUrl().get()).defaultCache();
            } catch (URISyntaxException e) {
                throw ExceptionUtil.createDescriptiveWrapper(RuntimeException::new, "Invalid URL",
                    e);
            }

            if (getParameters().getMaxAge().isPresent()) {
                builder.maxAge(getParameters().getMaxAge().get());
            }

            if (getParameters().getSha1().isPresent()) {
                builder.sha1(getParameters().getSha1().get());
            }

            if (getParameters().getIsOffline().get()) {
                builder.offline();
            }

            try {
                builder.downloadPath(getParameters().getOutputFile().get().getAsFile().toPath());
            } catch (DownloadException e) {
                throw ExceptionUtil.createDescriptiveWrapper(RuntimeException::new,
                    "Failed to download file", e);
            }
        }
    }
}

package com.frever.platform.mediaConvert;

import static com.google.common.primitives.Ints.max;
import static java.util.stream.Collectors.toList;

import com.frever.platform.mediaConvert.utils.Bigness;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.eclipse.microprofile.config.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@QuarkusMain
public class Main {
    // java -Dmedia.files.folder=/home/ec2-user/media-files -jar frever-platform-media-converter-runner.jar
    // java -Dmedia.files.folder=/home/ec2-user/media-files -Dmedia.convert.concurrency=3 -jar frever-platform-media-converter-runner.jar
    // java -Dmedia.files.folder=/home/ec2-user/media-files -Dbigness.threshold=Big -jar frever-platform-media-converter-runner.jar
    public static void main(String[] args) {
        Quarkus.run(MediaConverterApp.class, args);
    }

    public static class MediaConverterApp implements QuarkusApplication {
        private static final Logger LOG = LoggerFactory.getLogger(MediaConverterApp.class);

        @Override
        public int run(String... args) throws Exception {
            LOG.info("Start MediaConverterApp...");
            String mediaFilesFolder = getMediaFilesFolder();
            LOG.info("Will convert Media files from folder: {}", mediaFilesFolder);
            int concurrency = ConfigProvider.getConfig()
                .getOptionalValue("media.convert.concurrency", Integer.class)
                .orElseGet(() -> max(1, Runtime.getRuntime().availableProcessors() / 8));
            LOG.info("Will use concurrency when converting media files: {}", concurrency);
            Bigness threshold =
                ConfigProvider.getConfig().getOptionalValue("bigness.threshold", Bigness.class).orElse(Bigness.Normal);
            LOG.info("Bigness threshold: {}", threshold);
            List<String> folderNamesBelowThreshold = Bigness.getFolderNamesBelowThreshold(threshold);
            List<String> mediaFilesFolders = folderNamesBelowThreshold.stream()
                .map(folderName -> mediaFilesFolder + "/" + folderName)
                .collect(toList());
            final ExecutorService es = concurrency != 0 ? new ThreadPoolExecutor(
                concurrency,
                concurrency,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(concurrency),
                new ThreadPoolExecutor.CallerRunsPolicy()
            ) : null;
            for (String filesFolder : mediaFilesFolders) {
                MediaConverter.convert(filesFolder, es);
            }
            es.shutdown();
            try {
                if (!es.awaitTermination(30, TimeUnit.MINUTES)) {
                    es.shutdownNow();
                }
            } catch (InterruptedException e) {
                LOG.error("Error while waiting for executor service to finish", e);
                es.shutdownNow();
                Thread.currentThread().interrupt();
            }
            LOG.info("Finished MediaConverterApp...");
            Quarkus.waitForExit();
            return 0;
        }

        private static String getMediaFilesFolder() {
            String mediaFilesFolder = ConfigProvider.getConfig().getValue("media.files.folder", String.class);
            if (mediaFilesFolder.endsWith("/")) {
                mediaFilesFolder = mediaFilesFolder.substring(0, mediaFilesFolder.length() - 1);
            }
            return mediaFilesFolder;
        }
    }
}

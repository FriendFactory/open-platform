package com.frever.platform.mediaConvert;

import com.google.common.base.Stopwatch;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ws.schild.jave.ConversionOutputAnalyzer;
import ws.schild.jave.EncoderException;
import ws.schild.jave.encode.AudioAttributes;
import ws.schild.jave.encode.VideoAttributes;
import ws.schild.jave.process.ProcessLocator;
import ws.schild.jave.process.ProcessWrapper;
import ws.schild.jave.process.ffmpeg.DefaultFFMPEGLocator;
import ws.schild.jave.utils.RBufferedReader;

/**
 * -Djava.util.logging.manager=org.jboss.logmanager.LogManager
 */
public class MediaConverter {
    private static final Logger LOG = LoggerFactory.getLogger(MediaConverter.class);
    private static final Pattern SUCCESS_PATTERN =
        Pattern.compile(
            "^\\s*video\\:\\S+\\s+audio\\:\\S+\\s+subtitle\\:\\S+\\s+global headers\\:\\S+.*$",
            Pattern.CASE_INSENSITIVE
        );
    private static final String OUTPUT_FOLDER_NAME = "output";
    private static final ProcessLocator PROCESS_LOCATOR = new DefaultFFMPEGLocator();

    public static void convert(String mediaFilesFolder, ExecutorService es) throws IOException {
        File outputFolder = new File(mediaFilesFolder, OUTPUT_FOLDER_NAME);
        if (!outputFolder.exists() && !outputFolder.mkdirs()) {
            LOG.warn("Could not create output folder: {}", outputFolder);
        }
        LOG.info("---------- Start to convert media files in folder: " + mediaFilesFolder + " ---------- ");
//        Stopwatch stopWatch = Stopwatch.createStarted();
        Files.list(Path.of(mediaFilesFolder))
            .filter(Files::isRegularFile)
            .filter(path -> path.getFileName().toString().endsWith(".mp4"))
            .forEach(path -> {
                if (es != null) {
                    es.submit(() -> convertMediaFile(outputFolder, path));
                } else {
                    convertMediaFile(outputFolder, path);
                }
            });
//        LOG.info(
//            "---------- Finished converting media files in {} ms ---------- ",
//            stopWatch.elapsed(TimeUnit.MILLISECONDS)
//        );
    }

    private static void convertMediaFile(File outputFolder, Path path) {
        Stopwatch stopWatch = Stopwatch.createStarted();
        try (ProcessWrapper ffmpeg = PROCESS_LOCATOR.createExecutor()) {
            LOG.info("Will convert media file : {}", path);
            String fileName = path.getFileName().toString();
            String fileNameWithoutExtension = fileName.substring(0, fileName.lastIndexOf("."));
            File outputFolderForFile = new File(outputFolder, fileNameWithoutExtension);
            if (!outputFolderForFile.exists() && !outputFolderForFile.mkdirs()) {
                LOG.warn("Could not create output folder {} for media file {}", outputFolderForFile, path);
            }
            ffmpeg.addArgument("-i");
            ffmpeg.addArgument(path.toString());
            addArgumentsForAppleSdAndHd(ffmpeg, outputFolderForFile);
            addArgumentsForRawFormat(ffmpeg, outputFolderForFile);
            try {
                ffmpeg.execute();
                String line;
                ConversionOutputAnalyzer outputAnalyzer = new ConversionOutputAnalyzer(0, null);
                RBufferedReader reader = new RBufferedReader(new InputStreamReader(ffmpeg.getErrorStream()));
                while ((line = reader.readLine()) != null) {
                    outputAnalyzer.analyzeNewLine(line);
                }
                if (outputAnalyzer.getLastWarning() != null) {
                    if (!SUCCESS_PATTERN.matcher(outputAnalyzer.getLastWarning()).matches()) {
                        throw new RuntimeException(
                            "No match for: " + SUCCESS_PATTERN + " in " + outputAnalyzer.getLastWarning());
                    }
                }
                int exitCode = ffmpeg.getProcessExitCode();
                if (exitCode != 0) {
                    throw new RuntimeException("Exit code of ffmpeg encoding run is " + exitCode);
                }
            } catch (EncoderException | IOException e) {
                throw new RuntimeException(e);
            } finally {
                LOG.info(
                    "Finished converting media file {} in {} ms",
                    path,
                    stopWatch.elapsed(TimeUnit.MILLISECONDS)
                );
            }
        }
    }

    private static void addArgumentsForAppleSdAndHd(ProcessWrapper ffmpeg, File outputFolderForFile) {
        ffmpeg.addArgument("-map");
        ffmpeg.addArgument("0:v:0");
        ffmpeg.addArgument("-map");
        ffmpeg.addArgument("0:a:0");
        ffmpeg.addArgument("-map");
        ffmpeg.addArgument("0:v:0");
        ffmpeg.addArgument("-map");
        ffmpeg.addArgument("0:a:0");
        ffmpeg.addArgument("-vcodec");
        ffmpeg.addArgument("h264");
        ffmpeg.addArgument("-c:a");
        ffmpeg.addArgument("aac");
        ffmpeg.addArgument("-r");
        ffmpeg.addArgument("30");
        ffmpeg.addArgument("-ar");
        ffmpeg.addArgument("48000");
        ffmpeg.addArgument("-filter:v:0");
        ffmpeg.addArgument("scale=w=450:h=800");
        ffmpeg.addArgument("-b:v:0");
        ffmpeg.addArgument("1000k");
        ffmpeg.addArgument("-b:a:0");
        ffmpeg.addArgument("128k");
        ffmpeg.addArgument("-filter:v:1");
        ffmpeg.addArgument("scale=w=608:h=1080");
        ffmpeg.addArgument("-b:v:1");
        ffmpeg.addArgument("2000k");
        ffmpeg.addArgument("-b:a:1");
        ffmpeg.addArgument("128k");
        ffmpeg.addArgument("-var_stream_map");
        ffmpeg.addArgument("v:0,a:0,name:_SD v:1,a:1,name:_HD");
        ffmpeg.addArgument("-hls_list_size");
        ffmpeg.addArgument("0");
        ffmpeg.addArgument("-hls_time");
        ffmpeg.addArgument("1");
        ffmpeg.addArgument("-g");
        ffmpeg.addArgument("1");
//        ffmpeg.addArgument("-threads");
//        ffmpeg.addArgument("0");
        ffmpeg.addArgument("-f");
        ffmpeg.addArgument("hls");
        ffmpeg.addArgument("-hls_playlist_type");
        ffmpeg.addArgument("vod");
        ffmpeg.addArgument("-hls_flags");
        ffmpeg.addArgument("independent_segments");
        ffmpeg.addArgument("-master_pl_name");
        ffmpeg.addArgument("video.m3u8");
        ffmpeg.addArgument(outputFolderForFile.getAbsolutePath() + "/video%v.m3u8");
    }

    private static void addArgumentsForRawFormat(ProcessWrapper ffmpeg, File outputFolderForFile) {
        ffmpeg.addArgument("-s");
        ffmpeg.addArgument("608*1080");
        ffmpeg.addArgument("-vcodec");
        ffmpeg.addArgument("h264");
        ffmpeg.addArgument("-vb");
        ffmpeg.addArgument("3500000");
        ffmpeg.addArgument("-r");
        ffmpeg.addArgument("30");
        ffmpeg.addArgument("-acodec");
        ffmpeg.addArgument("aac");
        ffmpeg.addArgument("-ab");
        ffmpeg.addArgument("96k");
        ffmpeg.addArgument("-ar");
        ffmpeg.addArgument("48k");
        ffmpeg.addArgument("-g");
        ffmpeg.addArgument("90");
        ffmpeg.addArgument(outputFolderForFile.getAbsolutePath() + "/video-raw.mp4");
    }

    enum EncodingFormat {
        SD("_SD"), HD("_HD"), RAW("_raw"), THUMBNAIL("_thumbnail");

        EncodingFormat(String outputName) {
            this.outputName = outputName;
        }

        private static final VideoAttributes SD_VIDEO_ATTRIBUTES = new VideoAttributes();
        private static final AudioAttributes SD_AUDIO_ATTRIBUTES = new AudioAttributes();
        private static final VideoAttributes HD_VIDEO_ATTRIBUTES = new VideoAttributes();
        private static final AudioAttributes HD_AUDIO_ATTRIBUTES = new AudioAttributes();
        private static final VideoAttributes RAW_VIDEO_ATTRIBUTES = new VideoAttributes();
        private static final AudioAttributes RAW_AUDIO_ATTRIBUTES = new AudioAttributes();
        private static final VideoAttributes THUMBNAIL_VIDEO_ATTRIBUTES = new VideoAttributes();
        private static final AudioAttributes THUMBNAIL_AUDIO_ATTRIBUTES = null;

        static {
            SD.videoAttributes = SD_VIDEO_ATTRIBUTES;
            SD.audioAttributes = SD_AUDIO_ATTRIBUTES;

            HD.videoAttributes = HD_VIDEO_ATTRIBUTES;
            HD.audioAttributes = HD_AUDIO_ATTRIBUTES;

            RAW.videoAttributes = RAW_VIDEO_ATTRIBUTES;
            RAW.audioAttributes = RAW_AUDIO_ATTRIBUTES;

            THUMBNAIL.videoAttributes = THUMBNAIL_VIDEO_ATTRIBUTES;
            THUMBNAIL.audioAttributes = THUMBNAIL_AUDIO_ATTRIBUTES;
        }

        private VideoAttributes videoAttributes;
        private AudioAttributes audioAttributes;
        private String outputName;
    }
}

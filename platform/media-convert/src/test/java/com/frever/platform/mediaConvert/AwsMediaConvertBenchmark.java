package com.frever.platform.mediaConvert;

import com.frever.platform.mediaConvert.utils.Bigness;
import com.google.common.base.CharMatcher;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.mediaconvert.MediaConvertClient;
import software.amazon.awssdk.services.mediaconvert.model.CreateJobResponse;
import software.amazon.awssdk.services.mediaconvert.model.FileGroupSettings;
import software.amazon.awssdk.services.mediaconvert.model.GetJobTemplateRequest;
import software.amazon.awssdk.services.mediaconvert.model.HlsGroupSettings;
import software.amazon.awssdk.services.mediaconvert.model.Job;
import software.amazon.awssdk.services.mediaconvert.model.JobTemplate;
import software.amazon.awssdk.services.mediaconvert.model.OutputGroup;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Object;

public class AwsMediaConvertBenchmark {
    public static final String AWS_CONVERTED_S3_PATH = "s3://frever-platform/aws-converted/";
    public static final Bigness BIGNESS_THRESHOLD = Bigness.Bigger;

    public static void main(String[] args) {
        try (S3Client s3Client =
                 S3Client.builder().httpClient(ApacheHttpClient.create()).region(Region.EU_CENTRAL_1).build();
             MediaConvertClient mediaConvertClient = MediaConvertClient.builder()
                 .httpClient(ApacheHttpClient.create())
                 .endpointOverride(URI.create("https://yk2lhke4b.mediaconvert.eu-central-1.amazonaws.com"))
                 .region(Region.EU_CENTRAL_1)
                 .build()) {
            List<S3Object> mediaFiles =
                s3Client.listObjectsV2(builder -> builder.bucket("frever-platform").prefix("media-files")).contents();
            JobTemplate jobTemplate =
                mediaConvertClient.getJobTemplate(GetJobTemplateRequest.builder().name("video-conversion").build())
                    .jobTemplate();
            for (var s3Object : mediaFiles) {
                Long size = s3Object.size();
                var inputKey = s3Object.key();
                System.out.println("inputKey = " + inputKey + ", size = " + size);
                if (!passBignessThreshold(inputKey, BIGNESS_THRESHOLD)) {
                    continue;
                }
                String fileName = inputKey.substring(inputKey.lastIndexOf("/") + 1);
                String fileNameWithoutExtension = fileName.substring(0, fileName.lastIndexOf("."));
                List<OutputGroup> outputGroups = getOutputGroups(jobTemplate, fileNameWithoutExtension);
                CreateJobResponse jobResponse = mediaConvertClient.createJob(builder -> {
                    builder.queue("dev-2");
                    builder.jobTemplate(jobTemplate.name());
                    builder.role("arn:aws:iam::722913253728:role/sergii-dev-media-convert-manual-role");
                    builder.settings(jobSettings -> {
                        jobSettings.inputs(input -> {
                            input.fileInput("s3://frever-platform/" + inputKey);
                        });
                        jobSettings.outputGroups(outputGroups);
                    });
                });
                Job job = jobResponse.job();
                System.out.println(
                    "Job Arn: " + job.arn() + " , Created At: " + job.createdAt() + ", for file: " + inputKey);
            }
        }
    }

    public static boolean passBignessThreshold(String s3Key, Bigness threshold) {
        List<String> folderNamesBelowThreshold = Bigness.getFolderNamesBelowThreshold(threshold);
        for (String folderName : folderNamesBelowThreshold) {
            if (CharMatcher.is('/').countIn(s3Key) == 1) {
                return true;
            }
            if (s3Key.contains("/" + folderName + "/")) {
                return true;
            }
        }
        return false;
    }

    private static List<OutputGroup> getOutputGroups(JobTemplate jobTemplate, String path) {
        List<OutputGroup> outputGroups = new ArrayList<>();
        jobTemplate.settings().outputGroups().stream().forEach(outputGroup -> {
            HlsGroupSettings hlsGroupSettings = outputGroup.outputGroupSettings().hlsGroupSettings();
            if (hlsGroupSettings != null) {
                outputGroups.add(outputGroup.copy(outputGroupBuilder -> {
                    outputGroupBuilder.outputGroupSettings(outputGroupSettingsBuilder -> {
                        outputGroupSettingsBuilder.hlsGroupSettings(hlsGroupSettingsBuilder -> {
                            hlsGroupSettingsBuilder.destination(AWS_CONVERTED_S3_PATH + path + "/");
                        });
                    });
                }));
            }
            FileGroupSettings fileGroupSettings = outputGroup.outputGroupSettings().fileGroupSettings();
            if (fileGroupSettings != null) {
                outputGroups.add(outputGroup.copy(outputGroupBuilder -> {
                    outputGroupBuilder.outputGroupSettings(outputGroupSettingsBuilder -> {
                        outputGroupSettingsBuilder.fileGroupSettings(fileGroupSettingsBuilder -> {
                            fileGroupSettingsBuilder.destination(AWS_CONVERTED_S3_PATH + path + "/");
                        });
                    });
                }));
            }
        });
        return outputGroups;
    }
}

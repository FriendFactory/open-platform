package com.frever.ml.utils;

import java.util.Arrays;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class UtilityTests {
    @Test
    public void testDestinationBucketPathSplit() {
        var destinationBucketPath = "s3://frever-dev/Video/36763/NonLevel/21402/6892a177ec4642d1a7f9eabfa1f1f70b/video";
        var split = Utils.getDestinationS3BucketAndKey(destinationBucketPath);
        System.out.println(Arrays.toString(split));
        Assertions.assertAll(
            () -> Assertions.assertEquals("frever-dev", split[0]),
            () -> Assertions.assertEquals("Video/36763/NonLevel/21402/6892a177ec4642d1a7f9eabfa1f1f70b/video", split[1])
        );
    }

    @Test
    public void testFileNameGenerationForFaceSwap() {
        var videoFileName = "Video-2223-53446-0415e992c8a34c6bba2732e2593e20fb-face-swap.mp4";
        var imageS3Key = "s3://frever-dev/Video/36763/NonLevel/21402/6892a177ec4642d1a7f9eabfa1f1f70b/image.jpg";
        var imageFileExtension = imageS3Key.substring(imageS3Key.lastIndexOf("."));
        Assertions.assertEquals(".jpg", imageFileExtension);
        var imageFileName =
            videoFileName.subSequence(0, videoFileName.lastIndexOf(".")) + "-image" + imageFileExtension;
        Assertions.assertEquals("Video-2223-53446-0415e992c8a34c6bba2732e2593e20fb-face-swap-image.jpg", imageFileName);
    }
}

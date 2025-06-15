package com.frever.platform.operation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

public class S3EventTest {
    private static final String S3_EVENT = """
        {
          "Records": [
            {
              "eventVersion": "2.3",
              "eventSource": "aws:s3",
              "awsRegion": "eu-central-1",
              "eventTime": "2022-07-06T16:13:45.980Z",
              "eventName": "LifecycleExpiration:Delete",
              "userIdentity": {
                "principalId": "s3.amazonaws.com"
              },
              "requestParameters": {
                "sourceIPAddress": "s3.amazonaws.com"
              },
              "responseElements": {
                "x-amz-request-id": "6ECE04E06ED1F258",
                "x-amz-id-2": "2xVvO6v0PvCGcuQ0d653TM77ndyVZBFDIapbDgZuRHIv36lJszI7Q+Lj07mUmKTONuYSXDr79ELghhgCWMEAZtavFa0JRAmQDHkj4LVr2gk="
              },
              "s3": {
                "s3SchemaVersion": "1.0",
                "configurationId": "objects-deletion-event-Assets",
                "bucket": {
                  "name": "frever-content",
                  "ownerIdentity": {
                    "principalId": "A1UBMZRBC8Y9GG"
                  },
                  "arn": "arn:aws:s3:::frever-content"
                },
                "object": {
                  "key": "Assets/CameraAnimation/41552/CameraAnimation.txt",
                  "versionId": "D9gdpmLGEwdHqslYs1DLFxGf3MRCFRcf",
                  "sequencer": "005FC14C98DC7CA9E7"
                }
              }
            }
          ]
        }
        """;

    private static ObjectMapper mapper = new ObjectMapper();

    static {
        RegisterCustomModuleCustomizer customizer = new RegisterCustomModuleCustomizer();
        customizer.customize(mapper);
    }

    @Test
    public void testS3EventDeserialization() throws JsonProcessingException {
        S3Event event = mapper.readValue(S3_EVENT, S3Event.class);
        assertEquals(1, event.records.size());
        S3Event.Record record = event.records.get(0);
        assertEquals("2022-07-06T16:13:45.980Z", record.eventTime.toString());
        assertEquals("LifecycleExpiration:Delete", record.eventName);
        S3Event.S3Info s3 = record.s3;
        assertEquals("objects-deletion-event-Assets", s3.configurationId);
        S3Event.BucketInfo bucket = s3.bucket;
        assertEquals("frever-content", bucket.name);
        assertEquals("arn:aws:s3:::frever-content", bucket.arn);
        S3Event.ObjectInfo object = s3.object;
        assertEquals("Assets/CameraAnimation/41552/CameraAnimation.txt", object.key);
        assertEquals("D9gdpmLGEwdHqslYs1DLFxGf3MRCFRcf", object.versionId);
        assertEquals("005FC14C98DC7CA9E7", object.sequencer);
    }
}

package com.frever.platform.operation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.time.Instant;
import java.util.List;
import software.amazon.awssdk.services.sqs.model.Message;

@JsonIgnoreProperties(ignoreUnknown = true)
@RegisterForReflection
class S3Event {
    @JsonProperty("Records")
    List<Record> records;

    @JsonIgnoreProperties(ignoreUnknown = true)
    @RegisterForReflection
    static class Record {
        @JsonProperty
        Instant eventTime;
        @JsonProperty
        String eventName;
        @JsonProperty
        S3Info s3;
        Message originalMessage;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @RegisterForReflection
    static class S3Info {
        @JsonProperty
        String configurationId;
        @JsonProperty
        BucketInfo bucket;
        @JsonProperty
        ObjectInfo object;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @RegisterForReflection
    static class BucketInfo {
        @JsonProperty
        String name;
        @JsonProperty
        String arn;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @RegisterForReflection
    static class ObjectInfo {
        @JsonProperty
        String key;
        @JsonProperty
        String versionId;
        @JsonProperty
        String sequencer;
    }
}

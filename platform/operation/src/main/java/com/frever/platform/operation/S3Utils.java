package com.frever.platform.operation;

import static com.frever.platform.operation.DbUtils.PLATFORM_OPERATION_INPUT_QUEUE_URL;
import static java.lang.Math.min;

import io.quarkus.logging.Log;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteMarkerEntry;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsResponse;
import software.amazon.awssdk.services.s3.model.ObjectVersion;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;

public final class S3Utils {
    static final String LIFECYCLE_EXPIRATION_DELETE_MARKER_CREATED = "LifecycleExpiration:DeleteMarkerCreated";
    static final String OBJECT_REMOVED_DELETE = "ObjectRemoved:Delete";
    static final String FREVER_CONTENT_STAGE_BUCKET = "frever-content-stage";
    static final String FREVER_CONTENT_BUCKET = "frever-content";
    static final String OBJECT_REMOVED_DELETE_MARKER_CREATED = "ObjectRemoved:DeleteMarkerCreated";
    static final int DELAY_DAYS = 2;
    static final int EVENT_DELAY_HOURS = min(DELAY_DAYS * 24 / 12, 4);

    static S3Client getS3Client() {
        return S3Client.builder().httpClient(ApacheHttpClient.create()).region(Region.EU_CENTRAL_1).build();
    }

    static boolean oneDayBefore(Instant eventTime) {
        return daysBefore(eventTime, 1);
    }

    static boolean daysBefore(Instant eventTime, int days) {
        Instant now = Instant.now();
        return eventTime.plus(days, ChronoUnit.DAYS).isBefore(now);
    }

    static void handleDeleteFromObjectRemoved(S3Client s3Client, S3Event.Record record, AtomicBoolean deleteMessage) {
        Log.infof("Handle record with eventName: %s for bucket %s", record.eventName, record.s3.bucket.name);
        switch (record.s3.bucket.name) {
            case FREVER_CONTENT_STAGE_BUCKET -> {
                handleDeleteFromObjectRemovedStageEnv(s3Client, record, deleteMessage);
            }
            case FREVER_CONTENT_BUCKET -> {
                handleDeleteFromObjectRemovedProdEnv(s3Client, record, deleteMessage);
            }
            default -> {
                Log.errorf("Unknown bucket name: %s", record.s3.bucket.name);
            }
        }
    }

    static void handleDeleteFromObjectRemovedProdEnv(
        S3Client s3Client,
        S3Event.Record record,
        AtomicBoolean deleteMessage
    ) {
        handleDeleteFromObjectRemovedDeleteExpiredDeleteMarkersIfAny(s3Client, record, deleteMessage);
    }

    static void handleDeleteFromObjectRemovedStageEnv(
        S3Client s3Client,
        S3Event.Record record,
        AtomicBoolean deleteMessage
    ) {
        handleDeleteFromObjectRemovedDeleteExpiredDeleteMarkersIfAny(s3Client, record, deleteMessage);
    }

    static void handleDeleteFromObjectRemovedDeleteExpiredDeleteMarkersIfAny(S3Client s3Client, S3Event.Record record, AtomicBoolean deleteMessage) {
        String bucket = record.s3.bucket.name;
        String key = record.s3.object.key;
        ListObjectVersionsResponse versionsResponse = s3Client.listObjectVersions(r -> r.bucket(bucket).prefix(key));
        // No versions and delete markers left, all good, done
        if (!versionsResponse.hasVersions() && !versionsResponse.hasDeleteMarkers()) {
            Log.infof("Nothing in the bucket %s with key %s, done", bucket, key);
            deleteMessage.set(true);
            return;
        }
        // Only delete markers left, delete them
        if (!versionsResponse.hasVersions() && versionsResponse.hasDeleteMarkers()) {
            List<DeleteMarkerEntry> deleteMarkerEntries = versionsResponse.deleteMarkers();
            deleteAllDeleteMarkers(s3Client, bucket, deleteMarkerEntries);
            deleteMessage.set(true);
            return;
        }
        Log.infof("Versions left in the bucket %s with key %s, ignore message.", bucket, key);
        deleteMessage.set(true);
        return;
    }

    static void deleteAllDeleteMarkers(
        S3Client s3Client,
        String bucket,
        List<DeleteMarkerEntry> deleteMarkerEntries
    ) {
        deleteMarkerEntries.forEach(dm -> {
            Log.infof("Will delete delete marker with key %s", dm.key());
            s3Client.deleteObject(builder -> {
                builder.bucket(bucket);
                builder.key(dm.key());
                builder.versionId(dm.versionId());
            });
        });
    }

    static void handleDeleteMarkerCreatedFromObjectRemoved(
        SqsClient sqsClient,
        S3Client s3Client,
        S3Event.Record record,
        AtomicBoolean deleteMessage
    ) {
        Log.infof("Handle record with eventName: %s for bucket %s", record.eventName, record.s3.bucket.name);
        switch (record.s3.bucket.name) {
            case FREVER_CONTENT_STAGE_BUCKET -> {
                handleDeleteMarkerCreatedFromObjectRemovedStageEnv(s3Client, record, deleteMessage);
            }
            case FREVER_CONTENT_BUCKET -> {
                handleDeleteMarkerCreatedFromObjectRemovedProdEnv(sqsClient, s3Client, record, deleteMessage);
            }
            default -> {
                Log.errorf("Unknown bucket name: %s", record.s3.bucket.name);
                deleteMessage.set(true);
            }
        }
    }

    static void handleDeleteMarkerCreatedFromObjectRemovedProdEnv(
        SqsClient sqsClient,
        S3Client s3Client,
        S3Event.Record record,
        AtomicBoolean deleteMessage
    ) {
        Instant eventTime = record.eventTime;
        if (!daysBefore(eventTime, DELAY_DAYS)) {
            // Too early to handle, let's postpone the message for "EVENT_DELAY_HOURS" hours
            Message originalMessage = record.originalMessage;
            Log.infof(
                "Message %s time %s is less than %d day(s) old, check %d hours later...",
                originalMessage.messageId(),
                eventTime,
                DELAY_DAYS,
                EVENT_DELAY_HOURS
            );
            String receiptHandle = originalMessage.receiptHandle();
            sqsClient.changeMessageVisibility(builder -> {
                builder.queueUrl(PLATFORM_OPERATION_INPUT_QUEUE_URL);
                builder.receiptHandle(receiptHandle);
                builder.visibilityTimeout(EVENT_DELAY_HOURS * 60 * 60);
            });
            return;
        }
        deleteNonLastVersionsAndLastDeleteMarkers(s3Client, record, deleteMessage);
    }

    static void handleDeleteMarkerCreatedFromObjectRemovedStageEnv(
        S3Client s3Client,
        S3Event.Record record,
        AtomicBoolean deleteMessage
    ) {
        deleteNonLastVersionsAndLastDeleteMarkers(s3Client, record, deleteMessage);
    }

    static void deleteNonLastVersionsAndLastDeleteMarkers(
        S3Client s3Client,
        S3Event.Record record,
        AtomicBoolean deleteMessage
    ) {
        String bucket = record.s3.bucket.name;
        String key = record.s3.object.key;
        ListObjectVersionsResponse versionsResponse = s3Client.listObjectVersions(r -> r.bucket(bucket).prefix(key));
        if (versionsResponse.hasVersions()) {
            List<ObjectVersion> versions = versionsResponse.versions();
            Optional<ObjectVersion> lastVersion = versions.stream().filter(v -> v.isLatest()).findAny();
            if (lastVersion.isPresent()) {
                Log.infof("Found last version of %s/%s which is not a delete marker, ignore message.", bucket, key);
                deleteMessage.set(true);
                return;
            }
            deleteNonLastVersions(s3Client, bucket, versions);
        }

        if (!versionsResponse.hasDeleteMarkers()) {
            Log.infof("No delete marker found for %s/%s, ignore message.", bucket, key);
            deleteMessage.set(true);
            return;
        }
        List<DeleteMarkerEntry> deleteMarkerEntries = versionsResponse.deleteMarkers();
        Optional<DeleteMarkerEntry> deleteMarkerEntry =
            deleteMarkerEntries.stream().filter(d -> !d.isLatest()).findAny();
        if (deleteMarkerEntry.isPresent()) {
            Log.infof("Found none last version delete marker in %s/%s , ignore message.", bucket, key);
            deleteMessage.set(true);
            return;
        }
        deleteAllDeleteMarkers(s3Client, bucket, deleteMarkerEntries);
        deleteMessage.set(true);
    }

    static void deleteNonLastVersions(
        S3Client s3Client,
        String bucket,
        List<ObjectVersion> versions
    ) {
        versions.forEach(v -> {
            if (!v.isLatest()) {
                Log.infof("Will delete object with key %s", v.key());
                s3Client.deleteObject(builder -> {
                    builder.bucket(bucket);
                    builder.key(v.key());
                    builder.versionId(v.versionId());
                });
            }
        });
    }

    static void handleDefault(S3Event.Record record, AtomicBoolean deleteMessage) {
        Log.infof("Handle record with eventName: %s for bucket: %s, ignore", record.eventName, record.s3.bucket.name);
        deleteMessage.set(true);
    }

    static void handleDeleteMarkerCreatedFromLifecycle(
        S3Client s3Client,
        S3Event.Record record,
        AtomicBoolean deleteMessage
    ) {
        Log.infof(
            "Handle record with eventName: %s for bucket: %s, with object key %s",
            record.eventName,
            record.s3.bucket.name,
            record.s3.object.key
        );
    }
}

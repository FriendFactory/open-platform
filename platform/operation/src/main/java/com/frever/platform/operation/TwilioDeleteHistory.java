package com.frever.platform.operation;

import com.twilio.base.ResourceSet;
import com.twilio.exception.ApiException;
import com.twilio.rest.api.v2010.account.Call;
import com.twilio.rest.api.v2010.account.Message;
import io.quarkus.logging.Log;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

public final class TwilioDeleteHistory {
    private static int LOOK_BACK_DAYS = 7;
    private static int LOG_KEEP_DAYS = 30;

    public static void deleteOldSmsLogs() {
        for (int i = LOOK_BACK_DAYS + LOG_KEEP_DAYS; i >= LOG_KEEP_DAYS; i--) {
            ZonedDateTime time = ZonedDateTime.now(ZoneId.of("UTC")).minus(i, ChronoUnit.DAYS);
            try {
                ResourceSet<Message> messages =
                    Message.reader()
                        .setDateSent(time)
                        .read()
                        .setPageSize(2000);
                int size = 0;
                for (Message message : messages) {
                    Log.infof(
                        "DateSent: %s, Body: %s, Sid: %s, Status: %s",
                        message.getDateSent(),
                        message.getBody(),
                        message.getSid(),
                        message.getStatus()
                    );
                    try {
                        if (!Message.deleter(message.getSid()).delete()) {
                            Log.warnf("Failed to delete message: %s for time: %s", message.getSid(), time);
                        } else {
                            size++;
                        }
                    } catch (ApiException e) {
                        Log.warnf(e, "Failed to delete message: %s for time: %s", message.getSid(), time);
                    }
                }
                Log.infof("Deleted %s messages for time: %s", size, time);
            } catch (ApiException e) {
                Log.warnf(e, "Error deleting messages for time: %s", time);
            }
        }
    }

    public static void deleteOldCallLogs() {
        for (int i = LOOK_BACK_DAYS + LOG_KEEP_DAYS; i >= LOG_KEEP_DAYS; i--) {
            ZonedDateTime time = ZonedDateTime.now(ZoneId.of("UTC")).minus(i, ChronoUnit.DAYS);
            try {
                ResourceSet<Call> calls = Call.reader().setStartTime(time).read().setPageSize(2000);
                int size = 0;
                for (Call call : calls) {
                    Log.infof(
                        "Sid: %s, StartTime: %s, EndTime: %s, Status: %s",
                        call.getSid(),
                        call.getStartTime(),
                        call.getEndTime(),
                        call.getStatus()
                    );
                    try {
                        if (!Call.deleter(call.getSid()).delete()) {
                            Log.warnf("Failed to delete call: %s for time: %s", call.getSid(), time);
                        } else {
                            size++;
                        }
                    } catch (ApiException e) {
                        Log.warnf(e, "Failed to delete call: %s for time: %s", call.getSid(), time);
                    }
                }
                Log.infof("Deleted %s calls for time: %s", size, time);
            } catch (ApiException e) {
                Log.warnf(e, "Error deleting calls for time: %s", time);
            }
        }
    }
}

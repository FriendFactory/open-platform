package com.frever.platform.operation;

import com.twilio.converter.CurrencyDeserializer;
import com.twilio.rest.api.v2010.account.Call;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection(targets = {
    Message.class,
    Message.Direction.class,
    Message.Status.class,
    PhoneNumber.class,
    CurrencyDeserializer.class,
    Call.class,
    Call.Status.class,
    Call.UpdateStatus.class,
})
public class ReflectionConfiguration {
}

package com.frever.platform.timers.messaging;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TemplateUpdatedMessage(@JsonProperty("TemplateId") long templateId) {
}

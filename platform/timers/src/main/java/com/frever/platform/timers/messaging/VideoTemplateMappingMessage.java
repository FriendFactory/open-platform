package com.frever.platform.timers.messaging;

import com.fasterxml.jackson.annotation.JsonProperty;

public record VideoTemplateMappingMessage(@JsonProperty("VideoId") long videoId,
                                          @JsonProperty("OldTemplateIds") long[] oldTemplateIds,
                                          @JsonProperty("NewTemplateIds") long[] newTemplateIds) {
}

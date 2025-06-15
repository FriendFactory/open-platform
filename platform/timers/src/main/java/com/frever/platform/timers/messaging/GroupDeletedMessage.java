package com.frever.platform.timers.messaging;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GroupDeletedMessage(@JsonProperty("GroupId") long groupId) {

}

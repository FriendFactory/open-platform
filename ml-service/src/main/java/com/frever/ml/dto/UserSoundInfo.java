package com.frever.ml.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserSoundInfo(@JsonProperty("Id") long id, @JsonProperty("Name") String name,
                            @JsonProperty("EventId") long eventId) {
}

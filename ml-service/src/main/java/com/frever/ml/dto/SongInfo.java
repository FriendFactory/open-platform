package com.frever.ml.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SongInfo(@JsonProperty("Id") long id,
                       @JsonProperty("Artist") String artist,
                       @JsonProperty("Title") String title,
                       @JsonProperty("IsExternal") boolean isExternal,
                       @JsonProperty("Isrc") String iSrc) {
}

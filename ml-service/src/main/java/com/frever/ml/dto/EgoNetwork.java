package com.frever.ml.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EgoNetwork(@JsonProperty("first_hop") Set<Long> firstHop, @JsonProperty("second_hop") Set<Long> secondHop,
                         @JsonProperty("mutual") Set<Long> mutual) {
    public static EgoNetwork empty() {
        return new EgoNetwork(Set.of(), Set.of(), Set.of());
    }
}

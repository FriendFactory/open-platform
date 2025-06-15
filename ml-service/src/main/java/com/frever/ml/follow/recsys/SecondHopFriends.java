package com.frever.ml.follow.recsys;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SecondHopFriends(@JsonProperty("groupid") long groupId,
                               @JsonProperty("common_friends_list") List<Long> commonFriends) {
}

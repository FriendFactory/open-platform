package com.frever.ml.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RecommendedVideoResponse(@JsonProperty("VideoId") long videoId,
                                       @JsonProperty("GroupId") long groupId,
                                       @JsonProperty("ExternalSongIds") List<Long> externalSongIds,
                                       @JsonProperty("UserSoundInfo") UserSoundInfo[] userSoundInfo,
                                       @JsonProperty("SongInfo") SongInfo[] songInfo,
                                       @JsonProperty("Source") String source) {
}

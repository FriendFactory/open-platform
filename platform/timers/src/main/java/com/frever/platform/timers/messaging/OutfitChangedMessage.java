package com.frever.platform.timers.messaging;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record OutfitChangedMessage(@JsonProperty("Operation") String operation,
                                   @JsonProperty("Id") long id,
                                   @JsonProperty("ReadinessId") long readinessId,
                                   @JsonProperty("CreatedTime") Instant createdTime,
                                   @JsonProperty("ModifiedTime") Instant modifiedTime,
                                   @JsonProperty("Name") String name,
                                   @JsonProperty("GroupId") long groupId,
                                   @JsonProperty("Tags") int[] tags,
                                   @JsonProperty("FilesInfo") String filesInfo,
                                   @JsonProperty("SortOrder") int sortOrder,
                                   @JsonProperty("IsDeleted") boolean isDeleted,
                                   @JsonProperty("SaveMethod") String saveMethod) {
}

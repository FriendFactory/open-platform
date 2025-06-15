package com.frever.ml.dto;

import java.time.Instant;
import java.util.List;

public record VideoInfo(long videoId, long groupId, Instant createdTime, List<Long> externalSongIds, String songInfo,
                        String userSoundInfo, int startListItem, String country, String language,
                        Long generatedTemplateId) {
}

package com.frever.ml.dto;

import java.time.Instant;
import java.util.List;

public record CandidateVideo(VideoInfo videoInfo, int geoClusterPriority) {
    public long videoId() {
        return videoInfo.videoId();
    }

    public int startListItem() {
        return videoInfo.startListItem();
    }

    public String userSoundInfo() {
        return videoInfo.userSoundInfo();
    }

    public String language() {
        return videoInfo.language();
    }

    public String country() {
        return videoInfo.country();
    }

    public long groupId() {
        return videoInfo.groupId();
    }

    public List<Long> externalSongIds() {
        return videoInfo.externalSongIds();
    }

    public Instant createdTime() {
        return videoInfo.createdTime();
    }

    public String songInfo() {
        return videoInfo.songInfo();
    }

    public Long generatedTemplateId() {
        return videoInfo.generatedTemplateId();
    }
}

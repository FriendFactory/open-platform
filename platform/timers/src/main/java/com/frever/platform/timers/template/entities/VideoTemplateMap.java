package com.frever.platform.timers.template.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(schema = "stats", name = "video_template_map")
@IdClass(VideoIdAndTemplateId.class)
public class VideoTemplateMap {
    @Id
    @Column(name = "video_id")
    long videoId;
    @Id
    @Column(name = "template_id")
    long templateId;
    @Column(name = "mapped_timestamp")
    Instant mappedTimestamp;

    // For JPA
    protected VideoTemplateMap() {
    }

    public long getVideoId() {
        return videoId;
    }

    public long getTemplateId() {
        return templateId;
    }

    public Instant getMappedTimestamp() {
        return mappedTimestamp;
    }

    public VideoTemplateMap(long videoId, long templateId, Instant mappedTimestamp) {
        this.videoId = videoId;
        this.templateId = templateId;
        this.mappedTimestamp = mappedTimestamp;
    }
}

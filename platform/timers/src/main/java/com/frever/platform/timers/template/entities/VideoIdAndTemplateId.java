package com.frever.platform.timers.template.entities;

import java.io.Serializable;
import java.util.Objects;

public class VideoIdAndTemplateId implements Serializable {
    private Long videoId;
    private Long templateId;

    // For JPA
    protected VideoIdAndTemplateId() {
    }

    public VideoIdAndTemplateId(Long videoId, Long templateId) {
        this.videoId = videoId;
        this.templateId = templateId;
    }

    public Long getVideoId() {
        return videoId;
    }

    public void setVideoId(Long videoId) {
        this.videoId = videoId;
    }

    public Long getTemplateId() {
        return templateId;
    }

    public void setTemplateId(Long templateId) {
        this.templateId = templateId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VideoIdAndTemplateId that = (VideoIdAndTemplateId) o;
        return Objects.equals(videoId, that.videoId) && Objects.equals(templateId, that.templateId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(videoId, templateId);
    }
}

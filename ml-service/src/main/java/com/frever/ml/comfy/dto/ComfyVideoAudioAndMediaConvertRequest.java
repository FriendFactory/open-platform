package com.frever.ml.comfy.dto;

import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.annotations.providers.multipart.PartType;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;

public class ComfyVideoAudioAndMediaConvertRequest implements ComfyVideoAndAuxiliaryFileRequest {
    @FormParam("video")
    @PartType(MediaType.APPLICATION_OCTET_STREAM)
    private InputPart video;

    @FormParam("audio")
    @PartType(MediaType.APPLICATION_OCTET_STREAM)
    private InputPart audio;

    @FormParam("videoId")
    private long videoId;

    @FormParam("destinationBucketPath")
    private String destinationBucketPath;

    @Override
    public InputPart getVideo() {
        return video;
    }

    @Override
    public InputPart getAuxiliaryFile() {
        return audio;
    }

    public void setVideo(InputPart video) {
        this.video = video;
    }

    public InputPart getAudio() {
        return audio;
    }

    public void setAudio(InputPart audio) {
        this.audio = audio;
    }

    public long getVideoId() {
        return videoId;
    }

    public void setVideoId(long videoId) {
        this.videoId = videoId;
    }

    public String getDestinationBucketPath() {
        return destinationBucketPath;
    }

    public void setDestinationBucketPath(String destinationBucketPath) {
        this.destinationBucketPath = destinationBucketPath;
    }
}

package com.frever.ml.comfy.dto;

import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.annotations.providers.multipart.PartType;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;

public class ComfyPhotoAndPromptRequest {
    @FormParam("image")
    @PartType(MediaType.APPLICATION_OCTET_STREAM)
    private InputPart image;

    @FormParam("groupId")
    private long groupId;

    @FormParam("promptText")
    private String promptText;

    public InputPart getImage() {
        return image;
    }

    public void setImage(InputPart image) {
        this.image = image;
    }

    public long getGroupId() {
        return groupId;
    }

    public void setGroupId(long groupId) {
        this.groupId = groupId;
    }

    public String getPromptText() {
        return promptText;
    }

    public void setPromptText(String promptText) {
        this.promptText = promptText;
    }
}

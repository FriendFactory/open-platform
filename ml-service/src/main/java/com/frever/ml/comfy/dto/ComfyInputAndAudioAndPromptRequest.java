package com.frever.ml.comfy.dto;

import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.annotations.providers.multipart.PartType;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;

public class ComfyInputAndAudioAndPromptRequest {
    @FormParam("input")
    @PartType(MediaType.APPLICATION_OCTET_STREAM)
    private InputPart input;

    @FormParam("audio")
    @PartType(MediaType.APPLICATION_OCTET_STREAM)
    private InputPart audio;

    @FormParam("groupId")
    private long groupId;

    @FormParam("promptText")
    private String promptText;

    public InputPart getInput() {
        return input;
    }

    public void setInput(InputPart input) {
        this.input = input;
    }

    public InputPart getAudio() {
        return audio;
    }

    public void setAudio(InputPart audio) {
        this.audio = audio;
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

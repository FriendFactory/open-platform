package com.frever.ml.comfy;

public enum ComfyUiResultType {
    Video("filename"), Image("workflow"), ImageJpg("filename"), IsCached("text"), CacheComponents("");
    private final String jsonNode;

    ComfyUiResultType(String jsonNode) {
        this.jsonNode = jsonNode;
    }

    String getJsonNode() {
        return jsonNode;
    }
}

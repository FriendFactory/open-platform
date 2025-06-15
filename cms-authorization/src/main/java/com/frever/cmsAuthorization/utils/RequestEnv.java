package com.frever.cmsAuthorization.utils;

public enum RequestEnv {
    DEV("dev"),
    DEV_1("dev-1"),
    CONTENT_TEST("content-test"),
    CONTENT_STAGE("content-stage"),
    CONTENT_PROD("content-prod"),
    IXIA_PROD("ixia-prod");

    private String env;

    RequestEnv(String env) {
        this.env = env;
    }

    public String getEnv() {
        return env;
    }

    public static RequestEnv fromEnv(String env) {
        for (RequestEnv requestEnv : RequestEnv.values()) {
            if (requestEnv.getEnv().equals(env)) {
                return requestEnv;
            }
        }
        return null;
    }
}

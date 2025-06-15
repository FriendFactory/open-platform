package com.frever.platform.mediaConvert.utils;

import static java.util.stream.Collectors.toList;

import java.util.Arrays;
import java.util.List;

public enum Bigness {
    Normal(""), Big("big"), Bigger("bigger"), Biggest("biggest");

    private String folderName;

    public String getFolderName() {
        return folderName;
    }

    Bigness(String folderName) {
        this.folderName = folderName;
    }

    public static List<String> getFolderNamesBelowThreshold(Bigness threshold) {
        return Arrays.stream(Bigness.values())
            .filter(bigness -> bigness.ordinal() <= threshold.ordinal())
            .map(Bigness::getFolderName)
            .collect(toList());
    }
}

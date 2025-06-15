package com.frever.ml.utils;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public interface RandomUtils {
    static Random getRandom() {
        return ThreadLocalRandom.current();
    }
}

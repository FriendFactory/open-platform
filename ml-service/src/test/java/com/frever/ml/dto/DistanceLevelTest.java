package com.frever.ml.dto;

import com.frever.ml.dto.CandidateVideoWithDistanceLevel.DistanceLevel;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class DistanceLevelTest {
    @MethodSource("distanceLevelMapping")
    @ParameterizedTest
    public void testDistanceLevel(double distance, DistanceLevel expectedLevel) {
        Assertions.assertSame(DistanceLevel.getDistanceLevel(distance), expectedLevel);
    }

    static Stream<Arguments> distanceLevelMapping() {
        return Stream.of(
            Arguments.arguments(20d, DistanceLevel.Level1),
            Arguments.arguments(60d, DistanceLevel.Level2),
            Arguments.arguments(100d, DistanceLevel.Level3),
            Arguments.arguments(149d, DistanceLevel.Level3),
            Arguments.arguments(500d, DistanceLevel.Level11)
        );
    }
}

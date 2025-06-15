package com.frever.platform.timers.utils;

import java.time.LocalDate;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class UtilsTest {

    @Test
    public void testGenerateMonthlyPartitionInfo() {
        var monthlyPartitionInfo = Utils.generateMonthlyPartitionInfo("ChatMessage", 12);
        Assertions.assertEquals(12, monthlyPartitionInfo.size());
        var now = LocalDate.now();
        var year = now.getYear();
        var nextMonth = now.getMonthValue() + 1;
        for (var partitionInfo : monthlyPartitionInfo) {
            Assertions.assertEquals("ChatMessage", partitionInfo.tableName());
            Assertions.assertEquals(String.format("ChatMessage_%d%02d", year, nextMonth), partitionInfo.partitionName());
            Assertions.assertEquals(String.format("%d-%02d-01", year, nextMonth), partitionInfo.begin());
            nextMonth++;
            if (nextMonth > 12) {
                nextMonth = 1;
                year++;
            }
            Assertions.assertEquals(String.format("%d-%02d-01", year, nextMonth), partitionInfo.end());
            // System.out.println(partitionInfo);
        }
    }
    
    @Test
    public void testGenerateYearlyPartitionInfo() {
        var yearlyPartitionInfo = Utils.generateYearlyPartitionInfo("Likes", 3);
        Assertions.assertEquals(3, yearlyPartitionInfo.size());
        var now = LocalDate.now();
        var year = now.getYear();
        for (var partitionInfo : yearlyPartitionInfo) {
            Assertions.assertEquals("Likes", partitionInfo.tableName());
            Assertions.assertEquals(String.format("Likes_%d", year + 1), partitionInfo.partitionName());
            Assertions.assertEquals(String.format("%d-01-01", year + 1), partitionInfo.begin());
            Assertions.assertEquals(String.format("%d-01-01", year + 2), partitionInfo.end());
            year++;
            // System.out.println(partitionInfo);
        }
    }
}

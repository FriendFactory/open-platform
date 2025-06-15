package com.frever.platform.timers.partitionManagement;

public record PartitionInfo(String tableName, String partitionName, String begin, String end) {
}
package com.frever.platform.timers.utils;

import com.frever.platform.timers.partitionManagement.PartitionInfo;
import io.quarkus.runtime.configuration.ConfigUtils;
import java.lang.reflect.Array;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.util.*;

public final class Utils {

    private Utils() {
    }

    public static String getInParameterList(int parameterListSize) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parameterListSize; i++) {
            sb.append("?");
            if (i < parameterListSize - 1) {
                sb.append(",");
            }
        }
        return sb.toString();
    }

    public static List<Map<String, Object>> buildRows(ResultSet rs) throws SQLException {
        ResultSetMetaData metadata = rs.getMetaData();
        List<Map<String, Object>> retList = new ArrayList<>();
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 0; i < metadata.getColumnCount(); i++) {
                String columnName = metadata.getColumnLabel(i + 1);
                int type = metadata.getColumnType(i + 1);
                Object value;
                switch (type) {
                    case Types.DECIMAL:
                    case Types.NUMERIC:
                        value = rs.getBigDecimal(columnName);
                        break;
                    case Types.DOUBLE:
                    case Types.FLOAT: {
                        value = rs.getDouble(columnName);
                        break;
                    }
                    case Types.SMALLINT:
                    case Types.INTEGER:
                    case Types.BIGINT: {
                        value = rs.getLong(columnName);
                        break;
                    }
                    case Types.DATE:
                    case Types.TIMESTAMP:
                    case Types.TIME: {
                        Timestamp timestamp = rs.getTimestamp(columnName);
                        if (rs.wasNull()) {
                            value = null;
                        } else {
                            value = timestamp.toInstant();
                        }
                        break;
                    }
                    case Types.BOOLEAN:
                    case Types.BIT: {
                        value = rs.getBoolean(columnName) ? "1" : "0";
                        break;
                    }
                    default: {
                        value = rs.getString(columnName);
                    }
                }
                if (rs.wasNull()) {
                    row.put(columnName, null);
                } else {
                    row.put(columnName, value);
                }
            }
            retList.add(row);
        }
        return retList;
    }

    public static <T> List<Collection<T>> partition(Collection<T> largeCollection, int partitionSize) {
        if (partitionSize <= 0) {
            throw new IllegalArgumentException("Illegal partitionSize, was: " + partitionSize);
        }
        if (largeCollection == null) {
            return null;
        }
        List<Collection<T>> retList = new ArrayList<>(largeCollection.size() / partitionSize + 1);
        Collection<T> part = null;
        for (T v : largeCollection) {
            if (part != null && part.size() == partitionSize) {
                retList.add(part);
                part = null;
            }
            if (part == null) {
                part = switch (largeCollection) {
                    case SortedSet<?> __ -> new TreeSet<>();
                    case Set<?> __ -> new HashSet<>(partitionSize);
                    case List<?> __ -> new ArrayList<>(partitionSize);
                    case null, default -> throw new UnsupportedOperationException(
                        "Not prepared for this type of collection: " + largeCollection.getClass());
                };
            }
            part.add(v);
        }
        if (!empty(part)) {
            retList.add(part);
        }
        return retList;
    }

    public static boolean empty(Object o) {
        if (o == null) {
            return true;
        }
        if (o instanceof CharSequence) {
            return onlyWhitespace((CharSequence) o);
        }
        if (o.getClass().isArray()) {
            return Array.getLength(o) == 0;
        }
        if (o instanceof Collection) {
            return ((Collection<?>) o).isEmpty();
        }
        if (o instanceof Map) {
            return ((Map<?, ?>) o).isEmpty();
        }
        if (o instanceof Optional<?>) {
            return ((Optional<?>) o).isEmpty();
        }
        return false;
    }

    public static boolean onlyWhitespace(CharSequence cs) {
        int strLen;
        if (cs == null || (strLen = cs.length()) == 0) {
            return true;
        }
        for (int i = 0; i < strLen; i++) {
            int codePoint = Character.codePointAt(cs, i);
            if (!Character.isWhitespace(codePoint) && !Character.isSpaceChar(codePoint)) {
                return false;
            }
        }
        return true;
    }

    public static boolean notInProd() {
        List<String> profiles = ConfigUtils.getProfiles();
        return !profiles.contains("prod") && !profiles.contains("ixia-prod");
    }

    public static boolean inFreverProd() {
        List<String> profiles = ConfigUtils.getProfiles();
        return profiles.contains("prod");
    }

    public static boolean inIxiaProd() {
        List<String> profiles = ConfigUtils.getProfiles();
        return profiles.contains("ixia-prod");
    }

    public static List<PartitionInfo> generateMonthlyPartitionInfo(String tableName, int monthlyPartitionAhead) {
        if (monthlyPartitionAhead > 12) {
            throw new IllegalArgumentException("monthlyPartitionAhead must be less than or equal to 12");
        }
        var now = LocalDate.now();
        var year = now.getYear();
        var month = now.getMonthValue();
        List<PartitionInfo> partitionInfoList = new ArrayList<>(monthlyPartitionAhead);
        for (int i = 1; i <= monthlyPartitionAhead; i++) {
            var beginMonth = month + i;
            var beginYear = year;
            if (beginMonth > 12) {
                beginYear++;
                beginMonth -= 12;
            }
            var endYear = beginYear;
            var endMonth = beginMonth + 1;
            if (endMonth > 12) {
                endYear++;
                endMonth -= 12;
            }
            var partitionName = String.format("%s_%d%02d", tableName, beginYear, beginMonth);
            partitionInfoList.add(new PartitionInfo(
                tableName, partitionName, String.format("%d-%02d-01", beginYear, beginMonth),
                String.format("%d-%02d-01", endYear, endMonth)
            ));
        }
        return partitionInfoList;
    }

    public static List<PartitionInfo> generateYearlyPartitionInfo(String tableName, int yearlyPartitionAhead) {
        var now = LocalDate.now();
        var year = now.getYear();
        List<PartitionInfo> partitionInfoList = new ArrayList<>(yearlyPartitionAhead);
        for (int i = 1; i <= yearlyPartitionAhead; i++) {
            var partitionName = String.format("%s_%d", tableName, year + i);
            partitionInfoList.add(new PartitionInfo(
                tableName, partitionName, String.format("%d-01-01", year + i),
                String.format("%d-01-01", year + i + 1)
            ));
        }
        return partitionInfoList;
    }
}

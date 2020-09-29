package com.gin.pixivmanager.util;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * @author bx002
 */
public class TimeUtil {

    static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * 获得中国时区的当前时间 ZonedDateTime对象
     *
     * @return ZonedDateTime
     */
    public static ZonedDateTime getZonedDateTime() {
        return ZonedDateTime.now(ZoneId.of("Asia/Shanghai"));
    }

    /**
     * 获得当前时间的毫秒数
     *
     * @return 当前时间毫秒数
     */
    public static Long getTimestamps() {
        return getZonedDateTime().toInstant().toEpochMilli();
    }

    /**
     * 获得格式化日期输出
     *
     * @param pattern 格式模板
     * @return 日期
     */
    public static String getFormatTime(ZonedDateTime zonedDateTime, String pattern) {
        pattern = pattern == null ? "yyyy-MM-dd HH:mm:ss.SSS" : pattern;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        return formatter.format(zonedDateTime);
    }
}

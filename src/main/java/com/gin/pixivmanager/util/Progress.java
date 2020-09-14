package com.gin.pixivmanager.util;

import java.util.Map;

/**
 * 更新进度工具
 */
public class Progress {


    /**
     * 更新进度
     */
    public static void update(String questName, long count, long size, Map<String, String> progressMap) {
        if (progressMap == null) {
            return;
        }
        String v = calculateProgress(count, size);
        if (count == 0L) {
            progressMap.remove(questName);
        } else {
            progressMap.put(questName, v);
        }
    }

    /**
     * 格式化输出大小
     *
     * @param num 文件大小(B)
     * @return 字符串
     */
    static private String formatSize(long num) {
        String s = "" + num;
        int k = 1024;
        if (num > k * k) {
            double v = Math.floor(num * 100.0 / k / k) / 100;
            s = v + "M";
        } else if (num > 10 * k) {
            double v = Math.floor(num * 10.0 / k) / 10;
            s = v + "K";
        }
        return s;
    }

    /**
     * 计算进度
     *
     * @param count 计数器当前值
     * @param size  计数器最大值
     * @return 进度
     */
    private static String calculateProgress(long count, long size) {
        count = size - count;
        double percent = Math.floor(1.0 * count / size * 1000) / 10;

        String s = formatSize(count) + "/" + formatSize(size);

        return s + " " + percent;
    }

}

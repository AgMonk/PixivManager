package com.gin.pixivmanager.util;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * 更新进度工具
 */
@Slf4j
public class Progress implements Comparable<Progress> {
    /**
     * 任务名称
     */
    String name;
    /**
     * 当前和最大进度
     */
    long count;
    long size;
    int times;

    public Progress(String name, long count, long size) {
        this.name = name;
        this.count = count;
        this.size = size;
    }

    public Progress(String name, long size) {
        this(name, 0, size);
    }

    public Progress(String k, Map<String, Integer> map) {
        name = k;
        count = map.get("count");
        size = map.get("size");
        times = map.get("times");
    }

    /**
     * 进度是否完成
     *
     * @return 任务是否完成
     */
    public boolean isCompleted() {
        return count == size;
    }

    /**
     * 使任务完成
     */
    public void complete() {
        count = size;
    }

    /**
     * 增加当前进度
     *
     * @param num 增加进度
     * @return 当前进度
     */
    public long add(long num) {
        this.count += num;
        return this.count;
    }


    /**
     * 格式化输出大小
     *
     * @param num 文件大小(B)
     * @return 字符串
     */
    private static String inSize(long num) {
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
     * 绝对值进度
     *
     * @return 绝对值进度
     */
    public String getProgress() {
        return count + "/" + size;
    }

    /**
     * 文件大小进度
     *
     * @return 文件大小进度
     */
    public String getProgressInSize() {
        return inSize(count) + "/" + inSize(size);
    }

    /**
     * 百分比进度
     *
     * @return 百分比进度
     */
    public String getProgressInPercent() {
        return String.valueOf(Math.floor(1.0 * count / size * 1000) / 10);
    }

    @Override
    public String toString() {
        return getProgress() + " " + getProgressInSize() + " " + getProgressInPercent();
    }

    public String getName() {
        return name;
    }

    public int getTimes() {
        return times;
    }

    @Override
    public int compareTo(Progress o) {
        return this.name.compareTo(o.getName());
    }
}

package com.gin.pixivmanager.util;

import java.io.File;

/**
 * 文件工具类
 *
 * @author bx002
 */
public class FilesUtil {
    /**
     * 为文件创建目录
     *
     * @param file 文件
     * @return 执行是否成功
     */
    public static boolean mkParentDir(File file) {
        File parentFile = file.getParentFile();
        if (!parentFile.exists()) {
            return parentFile.mkdirs();
        }
        return false;
    }
}

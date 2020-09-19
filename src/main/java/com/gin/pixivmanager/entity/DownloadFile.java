package com.gin.pixivmanager.entity;

import lombok.Data;

/**
 * 等待下载的文件
 *
 * @author bx002
 */
@Data
public class DownloadFile {
    final private String path;
    final private String url;
    boolean downloading = false;

    public DownloadFile(String url, String path) {
        this.path = path;
        this.url = url;
    }
}

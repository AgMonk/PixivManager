package com.gin.pixivmanager.entity;

import lombok.Data;

import java.util.Objects;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DownloadFile that = (DownloadFile) o;
        return Objects.equals(url, that.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url);
    }
}

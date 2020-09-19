package com.gin.pixivmanager.service;

import com.gin.pixivmanager.entity.DownloadFile;

import java.util.List;

/**
 * 下载管理器
 *
 * @author bx002
 */
public interface DownloadManager {

    Integer add(List<DownloadFile> list);

    void download();
}

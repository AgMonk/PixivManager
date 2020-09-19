package com.gin.pixivmanager.dao;

import com.gin.pixivmanager.entity.DownloadFile;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @author bx002
 */
@Repository

public interface DownloadManagerMapper {
    /**
     * 获取下载文件列表
     *
     * @return 文件列表
     */
    List<DownloadFile> findDownloadFileList();

    /**
     * 添加下载文件列表
     *
     * @param list 文件列表
     * @return 添加数量
     */
    Integer addDownloadFileList(List<DownloadFile> list);

    /**
     * 删除一个文件
     *
     * @param downloadFile 文件
     */
    void remove(DownloadFile downloadFile);
}

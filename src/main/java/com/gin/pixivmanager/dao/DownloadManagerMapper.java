package com.gin.pixivmanager.dao;

import com.gin.pixivmanager.entity.DownloadFile;
import org.springframework.stereotype.Repository;

import java.util.Set;

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
    Set<DownloadFile> findDownloadFileList();

    /**
     * 添加下载文件列表
     *
     * @param set 文件列表
     * @return 添加数量
     */
    Integer addDownloadFileList(Set<DownloadFile> set);

    /**
     * 删除一个文件
     *
     * @param downloadFile 文件
     */
    void remove(DownloadFile downloadFile);

    /**
     * 添加慢搜索id
     *
     * @param pidSet id set
     * @return 数量
     */
    Integer addSlowSearchSet(Set<String> pidSet);

    /**
     * 获得慢搜索id
     *
     * @return
     */
    Set<String> getSlowSearchSet();

    /**
     * 移除慢搜索pid
     *
     * @param pidSet
     */
    Integer removeSlowSearchPid(Set<String> pidSet);
}

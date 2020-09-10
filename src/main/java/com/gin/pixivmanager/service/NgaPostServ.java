package com.gin.pixivmanager.service;

import java.io.File;
import java.util.Map;

/**
 * nga发帖service
 *
 * @author bx002
 */
public interface NgaPostServ {

    /**
     * 为上传准备附件文件
     *
     * @param ids id
     * @return 文件列表
     */
    Map<String, File> prepare4Files(String... ids);


    /**
     * 下载、复制指定文件到临时文件夹、转发到nga，删除临时文件
     *
     * @param ids pid
     * @return 发帖地址
     */
    String repost(String... ids);
}

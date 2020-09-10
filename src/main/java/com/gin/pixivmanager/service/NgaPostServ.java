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

}

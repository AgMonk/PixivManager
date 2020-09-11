package com.gin.pixivmanager.service;

/**
 * nga发帖service
 *
 * @author bx002
 */
public interface NgaPostServ {


    /**
     * 下载、复制指定文件到临时文件夹、转发到nga，删除临时文件
     *
     * @param name pid
     * @return 发帖地址
     */
    String repost(String f, String t, String... name);
}

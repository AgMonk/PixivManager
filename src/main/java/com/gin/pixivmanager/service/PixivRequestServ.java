package com.gin.pixivmanager.service;

import com.gin.pixivmanager.entity.Illustration;

import java.io.File;
import java.util.List;

/**
 * 向pixiv发送请求
 *
 * @author bx002
 */
public interface PixivRequestServ {
    /**
     * 下载多个文件
     *
     * @param detail  作品详情
     * @param rootDir 下载根目录
     */
    List<File> download(List<Illustration> detail, String rootDir);


    /**
     * 请求一个列表中的pid详情
     *
     * @param idList pid
     * @return 作品详情
     */
    List<Illustration> getIllustrationDetail(List<String> idList);


}

package com.gin.pixivmanager.service;

import com.gin.pixivmanager.entity.Illustration;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * 向pixiv发送请求
 *
 * @author bx002
 */
public interface PixivRequestServ {

    /**
     * 下载文件
     *
     * @param url      url
     * @param filePath 文件完整路径
     */
    void download(String url, String filePath);

    /**
     * 下载文件
     *
     * @param urlAndFilePath key为url value为filePath
     */
    void download(Map<String, String> urlAndFilePath);

    /**
     * 获取作品详情信息
     *
     * @param id    pid
     * @param list  接受结果的list
     * @param latch 倒数计数器
     */
    void getIllustrationDetail(String id, List<Illustration> list, CountDownLatch latch);

    /**
     * 请求一个列表中的pid详情
     *
     * @param idList pid
     * @return 作品详情
     */
    List<Illustration> getIllustrationDetail(List<String> idList);
}

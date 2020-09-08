package com.gin.pixivmanager.service;

import com.gin.pixivmanager.entity.Illustration;
import com.gin.pixivmanager.entity.Tag;

import java.util.List;
import java.util.Map;

/**
 * 数据管理对象
 *
 * @author bx002
 */
public interface DataManager {
    /**
     * 按照过滤条件输出tag
     *
     * @param page    页数
     * @param limit   每页条数
     * @param keyword 关键字
     * @param all     是否显示所有tag
     * @return
     */
    List<Tag> getTags(Integer page, Integer limit, String keyword, Integer all);

    /**
     * 给一个tag设置自定义翻译
     *
     * @param t
     * @return
     */
    Integer setTagTranslation(Tag t);

    /**
     * 数据初始化
     */
    void init();

    /**
     * 更新文件下载进度
     *
     * @param k
     * @param v
     * @return
     */
    String addDownloading(String k, String v);

    String addDetails(String k, String v);

    Map<String, String> getDetails();

    void addIllustration(Illustration i);

    Illustration getIllustration(String id);

    void addTag(Tag t);

    Tag getTag(String name);


    String addTranslation(Tag t);

    String getTranslation(String k);

    Map<String, String> getTranslationMap();

    Map<String, String> getDownloading();
}

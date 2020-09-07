package com.gin.pixivmanager.service;

import com.gin.pixivmanager.entity.Illustration;
import com.gin.pixivmanager.entity.Tag;

import java.util.List;

/**
 * 数据管理对象
 *
 * @author bx002
 */
public interface DataManager {
    /**
     * 排序输出未有自定义翻译的tag
     *
     * @param page
     * @param limit 数量
     * @return tag列表
     */
    List<Tag> getNotTranslatedTags(Integer page, Integer limit);

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
    String putDownloading(String k, String v);

    void addIllustration(Illustration i);

    Illustration getIllustration(String id);

    void addTag(Tag t);

    Tag getTag(String name);


    String addTranslation(Tag t);

    String getTranslation(String k);
}

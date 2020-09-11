package com.gin.pixivmanager.service;

import com.gin.pixivmanager.entity.Illustration;
import com.gin.pixivmanager.entity.Tag;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * 数据管理对象
 *
 * @author bx002
 */
public interface DataManager {
    Map<String, Illustration> getIllustrationMap();

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
    Integer addTranslation(Tag t);

    /**
     * 数据初始化
     */
    void init();

    /**
     * 添加作品详情到数据库和Map
     *
     * @param list 详情列表
     * @return 添加数量
     */
    Integer addIllustrations(List<Illustration> list);

    /**
     * 添加Tag到数据库和map
     *
     * @param list 作品详情列表
     * @return 新数量
     */
    Integer addTags(List<Illustration> list);

    /**
     * 更新文件下载进度
     *
     * @param questName
     * @param count
     * @param size
     * @return
     */
    String addDownloading(String questName, long count, long size);

    /**
     * 更新详情获取进度
     *
     * @param questName
     * @param count
     * @param size
     * @return
     */
    String addDetails(String questName, long count, long size);

    /**
     * 获取详情进度
     *
     * @return
     */
    Map<String, String> getDetails();

    /**
     * 获取下载进度
     *
     * @return
     */
    Map<String, String> getDownloading();

    /**
     * 获取翻译Map
     *
     * @return
     */
    Map<String, String> getTranslationMap();

    /**
     * 先从缓存中查找是否有数据 剩余项从数据库中查询
     *
     * @param idList id列表
     * @return 作品详情
     */
    List<Illustration> getIllustrations(List<String> idList);

    Map<String, File> getFilesMap();

    /**
     * 获得指定id的文件列表
     *
     * @param name
     * @return
     */
    Map<String, File> getFilesMap(String... name);

    void addFilesMap(List<File> list);

    /**
     * 获取根目录下的所有文件  pid - 文件路径的对应 用以显示图片
     *
     * @return
     */
    Map<String, String> getFilesPath();

    /**
     * 删除文件
     *
     * @param name
     * @return
     */
    String delFile(String name);
}

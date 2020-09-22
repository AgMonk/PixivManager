package com.gin.pixivmanager.service;

import com.gin.pixivmanager.entity.Illustration;
import com.gin.pixivmanager.entity.Tag;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 向pixiv发送请求
 *
 * @author bx002
 */
public interface PixivRequestServ {

    /**
     * 请求一个列表中的pid详情
     *
     * @param idSet        pid
     * @param idBookmarked
     * @return 作品详情
     */
    List<Illustration> getIllustrationDetail(Set<String> idSet, boolean idBookmarked);

    /**
     * 获取收藏的作品id
     *
     * @param tag  需要有的tag
     * @param page 最大获取数量
     * @return 收藏的作品id
     */
    Set<String> getBookmarks(String tag, Integer page);


    /**
     * 修改tag(批量)
     *
     * @param tag tag
     */
    void setTag(Tag tag);

    /**
     * 批量添加tag
     *
     * @param detail 详情
     */
    void addTags(List<Illustration> detail);

    /**
     * 文件归档(重命名)
     *
     * @param name id列表 格式  xxxx_p0
     * @return id列表
     */
    Set<String> archive(String[] name);

    /**
     * 下载多个作品 如果是收藏作品 添加tag
     *
     * @param illustList 作品列表
     * @param rootPath   下载根目录
     * @return 下载完成的文件
     */
    void downloadIllust(List<Illustration> illustList, String rootPath);


    /**
     * 搜索作品
     *
     * @param keywordAndPage 关键字和页数
     * @param all            是否显示所有作品  false时仅显示未收藏且未记录过的作品
     * @return 搜索结果
     */
    Set<Illustration> search(Set<String> keywordSet, Integer p, boolean all);

    /**
     * 搜索并下载作品
     *
     * @param keywordAndPage 关键字和页数
     * @param all            是否显示所有作品  false时仅显示未收藏且未记录过的作品
     * @return
     */
    Integer downloadSearch(Map<String, Integer> keywordAndPage, boolean all);
}

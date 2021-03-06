package com.gin.pixivmanager.service;

import com.gin.pixivmanager.entity.Illustration;
import com.gin.pixivmanager.entity.Tag;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

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
     * @param idSet         pid
     * @param idBookmarked  是否是已收藏作品
     * @param bookmarkCount
     * @return 作品详情
     */
    Set<Illustration> getIllustrationDetail(Set<String> idSet, boolean idBookmarked, Integer bookmarkCount);


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
    void addTags(Set<Illustration> detail);

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
     */
    void downloadIllust(Set<Illustration> illustList, String rootPath);


    /**
     * 搜索作品
     *
     * @param keywordSet 关键字
     * @param start      开始页码
     * @param end        结束页码
     * @param all        是否显示所有作品  false时仅显示未收藏且未记录过的作品
     * @param executor
     * @return 搜索结果
     */
    Set<Illustration> search(Set<String> keywordSet, Integer start, Integer end, boolean all, ThreadPoolTaskExecutor executor);

    /**
     * 慢搜索 存储关键字的搜索结果pid 缓慢获取详情
     *
     * @param keyword
     */
    void slowSearch(String keyword);

    /**
     * 慢详情
     */
    @Async(value = "slowDetailExecutor")
    void slowDetail();

    /**
     * 搜索并下载作品
     *
     * @param keywordAndPage 关键字和页数
     * @param all            是否显示所有作品  false时仅显示未收藏且未记录过的作品
     * @return
     */
    Integer downloadSearch(Map<String, Integer> keywordAndPage, boolean all);
}

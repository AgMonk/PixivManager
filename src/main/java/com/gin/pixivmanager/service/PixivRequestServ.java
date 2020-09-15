package com.gin.pixivmanager.service;

import com.gin.pixivmanager.entity.Illustration;
import com.gin.pixivmanager.entity.Tag;

import java.io.File;
import java.util.List;
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
     * @param idSet pid
     * @return 作品详情
     */
    List<Illustration> getIllustrationDetail(Set<String> idSet);

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
     * 文件归档(重命名)
     *
     * @param name id列表 格式  xxxx_p0
     * @return id列表
     */
    Set<String> archive(String[] name);

    /**
     * 下载多个作品 并添加tag
     *
     * @param illustList 作品列表
     * @param rootPath   下载根目录
     * @return 下载完成的文件
     */
    List<File> downloadIllustAndAddTags(List<Illustration> illustList, String rootPath);

}

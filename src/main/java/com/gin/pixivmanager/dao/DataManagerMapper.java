package com.gin.pixivmanager.dao;

import com.gin.pixivmanager.entity.Illustration;
import com.gin.pixivmanager.entity.Tag;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @author bx002
 */
@Repository
public interface DataManagerMapper {
    /**
     * 读取所有作品
     *
     * @return 详情
     */
    List<Illustration> getIllustrations();

    /**
     * 读取所有tag
     *
     * @return tag
     */
    List<Tag> getTags();

    /**
     * 读取所有翻译
     *
     * @return tag的翻译
     */
    List<Tag> getTrans();

    /**
     * 为tag添加翻译
     *
     * @param t tag
     * @return 结果
     */
    Integer addTranslation(Tag t);

    /**
     * 批量添加作品详情
     *
     * @param list 作品详情列表
     * @return 添加数量
     */
    Integer addIllustrations(List<Illustration> list);

    /**
     * 批量删除作品详情
     *
     * @param list 作品详情列表
     * @return 删除数量
     */
    Integer delIllustrations(List<Illustration> list);

    /**
     * 批量添加Tag
     *
     * @param list tag列表
     * @return 添加数量
     */
    Integer addTags(List<Tag> list);
}

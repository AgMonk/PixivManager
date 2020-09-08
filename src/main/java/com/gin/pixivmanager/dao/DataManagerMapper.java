package com.gin.pixivmanager.dao;

import com.gin.pixivmanager.entity.Illustration;
import com.gin.pixivmanager.entity.Tag;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * @author bx002
 */
@Mapper
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
    Integer setTagTranslation(Tag t);
}

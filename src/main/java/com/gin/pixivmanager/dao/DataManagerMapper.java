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
     * @return
     */
    List<Illustration> getIllustrations();

    /**
     * 读取所有tag
     *
     * @return
     */
    List<Tag> getTags();

    /**
     * 读取所有翻译
     *
     * @return
     */
    List<Tag> getTrans();

    Integer setTagTranslation(Tag t);
}

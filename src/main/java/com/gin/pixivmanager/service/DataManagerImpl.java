package com.gin.pixivmanager.service;

import com.gin.pixivmanager.dao.DataManagerMapper;
import com.gin.pixivmanager.entity.Illustration;
import com.gin.pixivmanager.entity.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
public class DataManagerImpl implements DataManager {
    final private Map<String, String> downloading = new HashMap<>();
    final private Map<String, Illustration> illustrationMap = new HashMap<>();
    final private Map<String, Tag> tagMap = new HashMap<>();
    final private Map<String, String> translationMap = new HashMap<>();

    final DataManagerMapper mapper;

    public DataManagerImpl(DataManagerMapper mapper) {
        this.mapper = mapper;

        init();

    }


    @Override
    public List<Tag> getNotTranslatedTags(Integer page, Integer limit) {
        limit = limit != null ? limit : 20;
        List<Tag> tagList = new ArrayList<>(tagMap.values());
        tagList.sort(Comparator.comparingInt(Tag::getCount));
        Collections.reverse(tagList);
        return tagList.subList((page - 1) * limit, page * limit);
    }


    @Override
    public Integer setTagTranslation(Tag t) {
        addTranslation(t);
        Tag tag = tagMap.get(t.getName());
        tag.setCount(0);
        tagMap.put(t.getName(), t);
        return mapper.setTagTranslation(t);
    }


    /**
     * 数据初始化
     */
    @Override
    public void init() {
        List<Illustration> illList = mapper.getIllustrations();
        illList.forEach(this::addIllustration);
        log.info("作品数量 {}", illustrationMap.size());
        List<Tag> tagList = mapper.getTags();
        tagList.forEach(this::addTag);
        log.info("tag数量 {}", tagMap.size());
        List<Tag> transList = mapper.getTrans();
        transList.forEach(this::addTranslation);
        log.info("自定义翻译数量 {}", translationMap.size());


        log.info("开始统计tag");

        countTags();

        log.info("数据载入完毕");
    }

    private void countTags() {
        List<Illustration> illList = new ArrayList<>(illustrationMap.values());
        for (Illustration ill : illList) {
            List<Tag> tagList = ill.getTagList();
            for (Tag tag : tagList) {
                String tagName = tag.getName();
                if (tagMap.containsKey(tagName)
                        && !translationMap.containsKey(tagName)
                        && !translationMap.containsKey(tagName.toLowerCase())
                ) {
                    Tag t = tagMap.get(tagName);
                    t.addCount();
                    tagMap.put(tagName, t);
                }
            }
        }

    }

    @Override
    public String putDownloading(String k, String v) {
        String complete = "100";
        if (v.endsWith(complete)) {
            downloading.remove(k);
        }
        return downloading.put(k, v);
    }

    @Override
    public void addIllustration(Illustration i) {
        illustrationMap.put(i.getId(), i);
    }

    @Override
    public Illustration getIllustration(String id) {
        return illustrationMap.get(id);
    }

    @Override
    public void addTag(Tag t) {
        tagMap.put(t.getName().toLowerCase(), t);
    }

    @Override
    public Tag getTag(String name) {
        return tagMap.get(name);
    }

    @Override
    public String addTranslation(Tag t) {
        return translationMap.put(t.getName().toLowerCase(), t.getTranslation());
    }

    @Override
    public String getTranslation(String k) {
        return translationMap.get(k);
    }

}

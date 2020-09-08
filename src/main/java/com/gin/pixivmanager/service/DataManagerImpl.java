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
    /**
     * 下载进度
     */
    final private Map<String, String> downloading = new HashMap<>();
    /**
     * 查询详情进度
     */
    final private Map<String, String> details = new HashMap<>();
    final private Map<String, Illustration> illustrationMap = new HashMap<>();
    final private Map<String, Tag> tagMap = new HashMap<>();
    final private Map<String, String> translationMap = new HashMap<>();

    final DataManagerMapper mapper;

    public DataManagerImpl(DataManagerMapper dataManagerMapper) {
        this.mapper = dataManagerMapper;

        init();

    }


    @Override
    public List<Tag> getTags(Integer page, Integer limit, String keyword, Integer all) {
        page = page != null ? page : 1;
        limit = limit != null ? limit : 20;
        List<Tag> tagList = new ArrayList<>(tagMap.values());

        if (keyword != null) {
            tagList.removeIf(tag -> {
                String s = keyword.toLowerCase();
                return !tag.getName().contains(s) && !tag.getTranslation().contains(s);
            });
        }
        if (all != 1) {
            tagList.removeIf(tag -> {
                return translationMap.containsKey(tag.getName()) || translationMap.containsKey(tag.getName().toLowerCase());
            });
        }

        tagList.sort(Comparator.comparingInt(Tag::getCount));
        Collections.reverse(tagList);
        return tagList.subList((page - 1) * limit, page * limit);
    }


    @Override
    public Integer setTagTranslation(Tag t) {
        log.info("添加Tag翻译 {} -> {}", t.getName(), t.getTranslation());
        addTranslation(t);
        Tag tag = tagMap.get(t.getName());
        tag.setCount(0);
        tagMap.put(t.getName(), t);
        /*todo 请求修改tag*/
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
                Tag t = tagMap.get(tagName);
                t = t != null ? t : tag;
                t.addCount();
                tagMap.put(tagName, t);
            }
        }

    }

    @Override
    public String addDownloading(String k, String v) {
        String complete = "100";
        if (v.endsWith(complete)) {
            return downloading.remove(k);
        }
        return downloading.put(k, v);
    }

    @Override
    public String addDetails(String k, String v) {
        String complete = "100";
        if (v.endsWith(complete)) {
            return details.remove(k);
        }
        return details.put(k, v);
    }

    @Override
    public Map<String, String> getDetails() {
        return details;
    }

    private void addIllustration(Illustration i) {
        illustrationMap.put(i.getId(), i);
    }

    @Override
    public Integer addIllustrations(List<Illustration> list) {
        Integer delCount = mapper.delIllustrations(list);
        log.info("删除作品详情 {} 个", delCount);
        Integer addCount = mapper.addIllustrations(list);
        log.info("添加作品详情 {} 个", addCount);
        list.forEach(this::addIllustration);
        return addCount;
    }


    @Override
    public Illustration getIllustration(String id) {
        return illustrationMap.get(id);
    }

    @Override
    public void addTag(Tag t) {
        tagMap.put(t.getName(), t);
    }

    @Override
    public Tag getTag(String name) {
        return tagMap.get(name);
    }

    @Override
    public String addTranslation(Tag t) {
        return translationMap.put(t.getName(), t.getTranslation());
    }

    @Override
    public String getTranslation(String k) {
        return translationMap.get(k);
    }

    @Override
    public Map<String, String> getTranslationMap() {
        return translationMap;
    }

    @Override
    public Map<String, String> getDownloading() {
        return downloading;
    }

    @Override
    public Integer addTags(List<Illustration> list) {
        List<Tag> tags = new ArrayList<>();
        for (Illustration ill : list) {
            List<Tag> tagList = ill.getTagList();
            for (Tag tag : tagList) {
                if (!tags.contains(tag) && !tagMap.containsKey(tag.getName())) {
                    tags.add(tag);
                    addTag(tag);
                }
            }
        }
        mapper.addTags(tags);
        int size = tags.size();
        log.info("添加新tag {} 个 总计 {}个", size, tagMap.size());
        return size;
    }
}

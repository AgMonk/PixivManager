package com.gin.pixivmanager.service;

import com.gin.pixivmanager.dao.DataManagerMapper;
import com.gin.pixivmanager.entity.Illustration;
import com.gin.pixivmanager.entity.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;

@Service
@Slf4j
public class DataManagerImpl implements DataManager {
    final private String COMPLETED = "100.0";

    /**
     * 下载进度
     */
    final private Map<String, String> downloading = new HashMap<>();
    /**
     * 查询详情进度
     */
    final private Map<String, String> details = new HashMap<>();

    /**
     * 作品数据
     */
    final private Map<String, Illustration> illustrationMap = new HashMap<>();
    /**
     * tag数据
     */
    final private Map<String, Tag> tagMap = new HashMap<>();
    /**
     * 自定义翻译数据
     */
    final private Map<String, String> translationMap = new HashMap<>();
    final private Executor serviceExecutor;
    final DataManagerMapper mapper;

    public DataManagerImpl(Executor serviceExecutor, DataManagerMapper dataManagerMapper) {
        this.serviceExecutor = serviceExecutor;
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
            tagList.removeIf(tag -> translationMap.containsKey(tag.getName()) || translationMap.containsKey(tag.getName().toLowerCase()));
        }

        tagList.sort(Comparator.comparingInt(Tag::getCount));
        Collections.reverse(tagList);

        List<Tag> subList = tagList.subList((page - 1) * limit, page * limit);

        subList.forEach(tag -> tag.createRecommendTranslation(translationMap));

        return subList;
    }


    /**
     * 数据初始化
     */
    @Override
    public void init() {
        CountDownLatch latch = new CountDownLatch(3);

        serviceExecutor.execute(() -> {
            List<Tag> tagList = mapper.getTags();
            tagList.forEach(this::addTag2Map);
            log.info("tag数量 {}", tagMap.size());
            latch.countDown();
        });
        serviceExecutor.execute(() -> {
            List<Tag> transList = mapper.getTrans();
            transList.forEach(this::addTranslation2Map);
            log.info("自定义翻译数量 {}", translationMap.size());
            latch.countDown();
        });
        serviceExecutor.execute(() -> {
            List<Illustration> illList = mapper.getIllustrations();
            illList.forEach(this::addIllustration2Map);
            log.info("作品数量 {}", illustrationMap.size());
            latch.countDown();
        });
        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        log.info("开始统计tag");

        countTags();

        log.info("数据载入完毕");

    }

    @Override
    public Integer addIllustrations(List<Illustration> list) {
        //分段插入的阈值
        int block = 50;

        Integer delCount = mapper.delIllustrations(list);
        log.info("删除作品详情 {} 个", delCount);
        Integer addCount = 0;

        if (list.size() <= block) {
            //直接插入
            addCount = mapper.addIllustrations(list);
        } else {
            //分段插入
            int i = 0;
            do {
                List<Illustration> subList = list.subList(i * block, Math.min(list.size(), (i + 1) * block));
                addCount += mapper.addIllustrations(subList);
                i++;
            } while ((i + 1) * block < list.size());
        }
        log.info("添加作品详情 {} 个", addCount);

        list.forEach(this::addIllustration2Map);
        return addCount;
    }

    @Override
    public Integer addTags(List<Illustration> list) {
        List<Tag> tags = new ArrayList<>();
        for (Illustration ill : list) {
            List<Tag> tagList = ill.getTagList();
            for (Tag tag : tagList) {
                if (!tags.contains(tag) && !tagMap.containsKey(tag.getName())) {
                    tags.add(tag);
                    addTag2Map(tag);
                }
            }
        }
        int size = tags.size();
        if (size > 0) {
            mapper.addTags(tags);
            log.info("添加新tag {} 个 总计 {}个", size, tagMap.size());
        }
        return size;
    }

    @Override
    public Integer addTranslation(Tag t) {
        log.info("添加Tag翻译 {} -> {}", t.getName(), t.getTranslation());
        addTranslation2Map(t);
        //清空使用次数
        Tag tag = tagMap.get(t.getName());
        tag.setCount(0);
        tagMap.put(t.getName(), t);
        return mapper.addTranslation(t);
    }


    @Override
    public Map<String, String> getTranslationMap() {
        return translationMap;
    }

    @Override
    public Map<String, String> getDownloading() {
        return downloading;
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
    public Map<String, String> getDetails() {
        return details;
    }

    @Override
    public String addDownloading(String questName, long count, long size) {
        return addProgress(questName, count, size, downloading);
    }

    @Override
    public String addDetails(String questName, long count, long size) {
        return addProgress(questName, count, size, details);
    }

    private String addProgress(String questName, long count, long size, Map<String, String> downloading) {
        String v = calculateProgress(count, size);
        if (v.endsWith(COMPLETED)) {
            return downloading.remove(questName);
        }
        return downloading.put(questName, v);
    }

    private static String calculateProgress(long count, long size) {
        int k = 1024;
        count = size - count;
        double percent = Math.floor(count * 1000.0 / size) / 10;

        String s;
        if (size > 100 * k) {
            count = count / k;
            size = size / k;
            s = count + "k/" + size + "k";
        } else {
            s = count + "/" + size;
        }

        return s + " " + percent;
    }

    private void addTag2Map(Tag t) {

        tagMap.put(t.getName(), t);
    }

    private void addIllustration2Map(Illustration i) {
        illustrationMap.put(i.getId(), i);
    }

    private String addTranslation2Map(Tag t) {
        return translationMap.put(t.getName(), t.getTranslation());
    }

    @Override
    public Map<String, Illustration> getIllustrationMap() {
        return illustrationMap;
    }

    /**
     * 先从缓存中查找是否有数据 剩余项从数据库中查询
     *
     * @param idList id列表
     * @return 作品详情
     */
    @Override
    public List<Illustration> getIllustrations(List<String> idList) {
        List<Illustration> list = new ArrayList<>();

        List<String> lackList = new ArrayList<>();
        for (String s : idList) {
            Illustration ill = illustrationMap.get(s);
            if (ill == null || ill.getUserId() == null) {
                lackList.add(s);
            } else {
                list.add(ill);
            }
        }

        if (lackList.size() > 0) {
            List<Illustration> detail = mapper.getIllustrationsById(lackList);
            list.addAll(detail);
            //放入缓存
            detail.forEach(this::addIllustration2Map);
        }

        return list;
    }
}

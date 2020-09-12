package com.gin.pixivmanager.service;

import com.gin.pixivmanager.dao.DataManagerMapper;
import com.gin.pixivmanager.entity.Illustration;
import com.gin.pixivmanager.entity.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class DataManagerImpl implements DataManager {
    /**
     * 常规文件名正则
     */
    final static Pattern PATTERN_ILLUST = Pattern.compile("\\d+_p\\d+");
    /**
     * 动图文件名正则
     */
    final static Pattern PATTERN_UGOIRA = Pattern.compile("\\d+_ugoira\\d+");


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
    private Map<String, Illustration> illustrationMap = new HashMap<>();
    /**
     * tag数据
     */
    private Map<String, Tag> tagMap = new HashMap<>();
    /**
     * 自定义翻译数据
     */
    private Map<String, String> translationMap = new HashMap<>();
    /**
     * 文件列表
     */
    private Map<String, File> filesMap;


    final private Executor serviceExecutor;
    final private DataManagerMapper mapper;
    final private UserInfo userInfo;

    public DataManagerImpl(Executor serviceExecutor, DataManagerMapper dataManagerMapper, UserInfo userInfo) {
        this.serviceExecutor = serviceExecutor;
        this.mapper = dataManagerMapper;
        this.userInfo = userInfo;

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

        List<Tag> subList = tagList.subList((page - 1) * limit, Math.min(page * limit, tagList.size()));

        subList.forEach(tag -> tag.createRecommendTranslation(translationMap));

        return subList;
    }


    /**
     * 数据初始化
     */
    @Override
    public void init() {
        CountDownLatch latch = new CountDownLatch(4);

        serviceExecutor.execute(() -> {
            tagMap = new HashMap<>();
            List<Tag> tagList = mapper.getTags();
            tagList.forEach(this::addTag2Map);
            log.info("tag数量 {}", tagMap.size());
            latch.countDown();
        });
        serviceExecutor.execute(() -> {
            translationMap = new HashMap<>();
            List<Tag> transList = mapper.getTrans();
            transList.forEach(this::addTranslation2Map);
            log.info("自定义翻译数量 {}", translationMap.size());
            latch.countDown();
        });
        serviceExecutor.execute(() -> {
            illustrationMap = new HashMap<>();
            List<Illustration> illList = mapper.getIllustrations();
            illList.forEach(this::addIllustration2Map);
            log.info("作品数量 {}", illustrationMap.size());
            latch.countDown();
        });
        serviceExecutor.execute(() -> {
            filesMap = new HashMap<>();

            //获取根目录下所有文件
            List<File> list = new ArrayList<>();
            String rootPath = userInfo.getRootPath();
            listFiles(new File(rootPath), list);

            addFilesMap(list);

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
        String COMPLETED = "100.0";
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
            s = s.contains("_") ? s.substring(0, s.indexOf("_")) : s;
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

    /**
     * 遍历目录下的所有文件
     *
     * @param file 根目录
     * @param list 用以存储的文件列表
     */
    private static void listFiles(File file, List<File> list) {
        File[] fs = file.listFiles();
        if (fs == null || fs.length == 0) {
            return;
        }
        for (File f : fs) {
            if (f.isDirectory()) {
                listFiles(f, list);
            }
            if (f.isFile()) {
                list.add(f);
            }
        }
    }

    @Override
    public Map<String, File> getFilesMap() {
        return filesMap;
    }

    @Override
    public Map<String, File> getFilesMap(String... name) {
        Map<String, File> map = new HashMap<>();
        for (String s : name) {
            if (filesMap.containsKey(s)) {
                map.put(s, filesMap.get(s));
            }
        }
        return map;
    }

    @Override
    public void addFilesMap(List<File> list) {
        if (list == null || list.size() == 0) {
            return;
        }
        for (File file : list) {
            String name = file.getName();
            String key = null;
            Matcher matcherIllust = PATTERN_ILLUST.matcher(name);
            Matcher matcherUgoira = PATTERN_UGOIRA.matcher(name);
            if (matcherIllust.find()) {
                key = matcherIllust.group();
            }
            if (matcherUgoira.find()) {
                String group = matcherUgoira.group();
                key = group.substring(0, group.indexOf("_"));
            }
            if (key != null) {
                filesMap.put(key, file);
            }
        }
    }

    @Override
    public Map<String, String> getFilesPath() {
        Map<String, String> map = new HashMap<>();
        for (Map.Entry<String, File> entry : filesMap.entrySet()) {
            String path = "/pixiv" + entry.getValue().getPath()
                    .replace("\\", "/")
                    .replace(userInfo.getRootPath(), "");
            //只返回图片
//            if (path.endsWith("jpg") || path.endsWith("png")) {
            map.put(entry.getKey(), path);
//            }
        }
        return map;
    }

    @Override
    public String delFile(String name) {
        if (filesMap.containsKey(name)) {
            File file = filesMap.get(name);
            if (file.delete()) {
                log.debug("删除文件 {}", file.getPath());
                filesMap.remove(name);
                return name;
            } else {
                log.warn("删除失败 {}", file.getPath());
            }
        }
        return null;
    }


}

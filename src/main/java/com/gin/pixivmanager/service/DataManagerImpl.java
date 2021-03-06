package com.gin.pixivmanager.service;

import com.gin.pixivmanager.dao.DataManagerMapper;
import com.gin.pixivmanager.dao.DownloadManagerMapper;
import com.gin.pixivmanager.entity.DownloadFile;
import com.gin.pixivmanager.entity.Illustration;
import com.gin.pixivmanager.entity.Tag;
import com.gin.pixivmanager.util.FilesUtil;
import com.gin.pixivmanager.util.Progress;
import com.gin.pixivmanager.util.Request;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
     * 推特命名正则
     */
    final static Pattern PATTERN_TWITTER_ID = Pattern.compile("\\[\\d+\\]\\[p_\\d+\\]");


    /**
     * 下载进度
     */
    final private Map<String, String> downloading = new HashMap<>();
    /**
     * 查询详情进度
     */
    final private Map<String, String> details = new HashMap<>();
    /**
     * 下载进度
     */
    final private Map<String, Map<String, Integer>> progressDownloading = new HashMap<>();
    /**
     * 主任务进度
     */
    final private Map<String, Map<String, Integer>> progressMain = new HashMap<>();

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
    /**
     * 下载文件列表
     */
    final private Set<DownloadFile> downloadFileSet;
    /**
     * 慢搜索pid set
     */
    final private Set<String> slowSearchPidSet;

    final private ThreadPoolTaskExecutor serviceExecutor;
    final private ThreadPoolTaskExecutor downloadExecutor;
    final private DownloadManagerMapper downloadManagerMapper;
    final private DataManagerMapper mapper;
    final private UserInfo userInfo;

    public DataManagerImpl(ThreadPoolTaskExecutor serviceExecutor, ThreadPoolTaskExecutor downloadExecutor, DownloadManagerMapper downloadManagerMapper, DataManagerMapper dataManagerMapper, UserInfo userInfo) {
        this.serviceExecutor = serviceExecutor;
        this.downloadExecutor = downloadExecutor;
        this.downloadManagerMapper = downloadManagerMapper;
        this.mapper = dataManagerMapper;
        this.userInfo = userInfo;

        downloadFileSet = downloadManagerMapper.findDownloadFileList();
        slowSearchPidSet = downloadManagerMapper.getSlowSearchSet();

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

            //设置翻译字典
            Illustration.setDic(translationMap);

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
            log.info("未分类作品数量 {}", list.size());
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
        int size = list.size();
        if (size == 0) {
            return 0;
        }
        //分段插入的阈值
        int block = 50;

        Integer delCount = mapper.delIllustrations(list);
        if (delCount > 0) {
            log.info("删除作品详情 {} 个", delCount);
        }
        Integer addCount = 0;

        if (size <= block) {
            //直接插入
            addCount = mapper.addIllustrations(list);
        } else {
            //分段插入
            int i = 0;
            do {
                List<Illustration> subList = list.subList(i * block, Math.min(size, (i + 1) * block));
                addCount += mapper.addIllustrations(subList);
                i++;
            } while ((i + 1) * block < size);
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

    @Override
    public Map<String, String> getDetails() {
        return details;
    }

    @Override
    public Map<String, Illustration> getIllustrationMap() {
        return illustrationMap;
    }

    /**
     * 先从缓存中查找是否有数据 剩余项从数据库中查询
     *
     * @param idSet id列表
     * @return 作品详情
     */
    @Override
    public Set<Illustration> getIllustrations(Set<String> idSet) {
        Set<Illustration> illustSet = new HashSet<>();

        //去掉下划线的 id set
        Set<String> set = idSet.stream().map(s -> (!s.contains("_") ? s : s.substring(0, s.indexOf("_")))).collect(Collectors.toSet());

        Set<Map.Entry<String, Illustration>> entrySet = illustrationMap.entrySet();

        //检查缓存中是否存在的详情数据 如存在则加入
        entrySet.stream()
                .filter(entry -> set.contains(entry.getKey()))
                .filter(entry -> entry.getValue().getUserId() != null)
                .forEach(entry -> illustSet.add(entry.getValue()));
        if (illustSet.size() > 0) {
            log.info("缓存中查询到 {} 条数据", illustSet.size());
        }

        //有缺少的详情数据 向数据库查询 将查询到的数据加入缓存
        if (illustSet.size() < set.size()) {
            List<String> lackPidList = entrySet.stream()
                    .filter(entry -> set.contains(entry.getKey()))
                    .filter(entry -> !illustSet.contains(entry.getValue()))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
            if (lackPidList.size() > 0) {
                List<Illustration> detailFromDatabase = mapper.getIllustrationsById(lackPidList);
                if (detailFromDatabase.size() > 0) {
                    illustSet.addAll(detailFromDatabase);
                    detailFromDatabase.forEach(this::addIllustration2Map);
                    log.info("数据库中查询到 {} 条数据", detailFromDatabase.size());
                }
            }
        }

        return illustSet;
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
            Matcher matcherTwitter = PATTERN_TWITTER_ID.matcher(name);
            if (matcherIllust.find()) {
                key = matcherIllust.group();
            }
            if (matcherUgoira.find()) {
                String group = matcherUgoira.group();
                key = group.substring(0, group.indexOf("_"));
            }
            if (matcherTwitter.find()) {
                String group = matcherTwitter.group();
                key = group.replace("[", "").replace("]", "");
            }
            if (key != null) {
                filesMap.put(key, file);
            }
        }
    }

    @Override
    public List<Map<String, String>> getFilesPath() {
        List<Map<String, String>> list = new ArrayList<>();
        List<String> keyList = new ArrayList<>(filesMap.keySet());

        keyList.sort((s1, s2) -> {
            if (s1.contains("_p") && s2.contains("_p")) {
                String[] p1 = s1.split("_p");
                String[] p2 = s2.split("_p");
                if (!p1[0].equals(p2[0])) {
                    return -1 * p1[0].compareTo(p2[0]);
                } else {
                    int i1 = Integer.parseInt(p1[1]);
                    int i2 = Integer.parseInt(p2[1]);
                    return i1 - i2;
                }
            } else if (s1.length() != s2.length()) {
                return s2.length() - s1.length();
            }
            return -1 * s1.compareTo(s2);

        });


        for (String s : keyList) {
            Map<String, String> map = new HashMap<>();
            String path = "/pixiv" + filesMap.get(s).getPath()
                    .replace("\\", "/")
                    .replace(userInfo.getRootPath(), "");
            map.put("name", s);
            map.put("path", path.replace("[", "%5B").replace("]", "%5D"));
            list.add(map);
        }

        return list;
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

    @Override
    public void addProgressMain(String k, Map<String, Integer> map) {
        k = getQuestName(k);
        log.debug("添加主任务 {} 0/{}", k, map.get("size"));
        synchronized (progressMain) {
            progressMain.put(k, map);
        }
    }


    @Override
    public void addProgressDownloading(String k, Map<String, Integer> map) {
        log.debug("添加下载任务 {}", k);
        synchronized (progressDownloading) {
            progressDownloading.put(k, map);
        }
    }

    @Override
    public Map<String, Map<String, Integer>> getProgressDownloading() {
        return progressDownloading;
    }

    @Override
    public Map<String, List<Progress>> getProgress() {
        HashMap<String, List<Progress>> map = new HashMap<>();
        synchronized (progressMain) {
            List<Progress> list = new ArrayList<>();
            progressMain.forEach((k, v) -> {
                list.add(new Progress(k, v));
            });
            map.put("main", list);
        }
        synchronized (progressDownloading) {
            List<Progress> list = new ArrayList<>();
            progressDownloading.forEach((k, v) -> {
                list.add(new Progress(k, v));
            });
            map.put("downloading", list);
        }
        return map;
    }

    @Override
    public void uploadTwitter(MultipartFile file, String title, String tags) {
        String fileName = file.getOriginalFilename();
        assert fileName != null;
        String count = fileName.contains("_") ? fileName.substring(fileName.indexOf("_") + 1, fileName.indexOf(".")) : "0";
        String suffix = fileName.substring(fileName.indexOf('.'));
        String id = fileName.contains("_") ? fileName.substring(0, fileName.indexOf("_")) : fileName.substring(0, fileName.indexOf("."));

        StringBuilder fileNameBuilder = new StringBuilder();
        fileNameBuilder
                .append("[").append(id).append("]")
                .append("[p_").append(count).append("]")
                .append("[title_").append(title).append("]")
                .append("[tags_").append(tags).append("]")
                .append(suffix)
        ;

        File destFile = new File(userInfo.getRootPath() + "/twitter/" + fileNameBuilder.toString());
        File parentFile = destFile.getParentFile();
        if (!parentFile.exists()) {
            if (!parentFile.mkdirs()) {
                log.error("创建文件夹失败 {}", parentFile.getPath());
            }
        }
        try {
            file.transferTo(destFile);

            log.info("添加推特图片 {} {}", id, destFile);

            filesMap.put(id + "p_" + count, destFile);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void moveFile(String[] name) {
        for (String s : name) {
            File file = getFilesMap().get(s);
            String sourcePath = file.getPath().replace("\\", "/");
            String destPath = sourcePath.replace(userInfo.getRootPath(), userInfo.getArchivePath());
            File destFile = new File(destPath);
            FilesUtil.mkParentDir(destFile);
            if (file.renameTo(destFile)) {
                log.info("移动文件 {} -> {}", file.getPath(), destFile.getPath());
                getFilesMap().remove(s);
            }
        }
    }


    @Override
    public Integer addDownload(Set<DownloadFile> set) {
        if (set.size() == 0) {
            return 0;
        }
        synchronized (downloadFileSet) {
            log.info("添加下载队列 {} 个", set.size());
            downloadFileSet.addAll(set);
            return downloadManagerMapper.addDownloadFileList(set);
        }
    }

    /**
     * 从列表中把 1个未正在下载的文件添加进队列
     */
    @Override
    public void download() {

        List<DownloadFile> list = new ArrayList<>(downloadFileSet);

        list.sort((o1, o2) -> {
            String name1 = o1.getPath();
            String name2 = o2.getPath();
            return -1 * name1.compareTo(name2);
        });

        for (DownloadFile downloadFile : list) {
            if (!downloadFile.isDownloading()) {
                downloadFile.setDownloading(true);
                downloadExecutor.execute(() -> {
                    File file = new File(downloadFile.getPath());
                    Request request = null;
                    try {
                        String fileName = downloadFile.getUrl().substring(downloadFile.getUrl().lastIndexOf("/") + 1);
                        Map<String, Integer> progressMap = Request.createProgressMap(100);
                        addProgressDownloading(fileName, progressMap);

                        request = Request.create(downloadFile.getUrl())
                                .setFile(file)
                                .setReferer(null)
                                .setProgressMap(progressMap)
                                .get();


                        downloadFileSet.remove(downloadFile);

                        if (!file.exists()) {
                            log.warn("文件未下载 {}", downloadFile.getUrl());
                            throw new RuntimeException("文件未下载" + downloadFile.getUrl());
                        }
                        
                        synchronized (downloadFileSet) {
                            downloadManagerMapper.remove(downloadFile);
                        }
                        List<File> files = new ArrayList<>();
                        files.add(file);
                        addFilesMap(files);
                    } catch (Exception e) {
                        downloadFile.setDownloading(false);
                        assert request != null;
                        request.complete();
                        e.printStackTrace();
                    }
                });
                if (downloadExecutor.getActiveCount() == downloadExecutor.getMaxPoolSize()) {
                    break;
                }
            }
        }
    }

    @Override
    public Integer addSlowSearchPid(Set<String> pidSet) {
        synchronized (slowSearchPidSet) {
            slowSearchPidSet.addAll(pidSet);
            downloadManagerMapper.removeSlowSearchPid(pidSet);
            return downloadManagerMapper.addSlowSearchSet(pidSet);
        }
    }

    @Override
    public Integer removeSlowSearchPid(Set<String> pidSet) {
        synchronized (slowSearchPidSet) {
            slowSearchPidSet.removeIf(pidSet::contains);
            return downloadManagerMapper.removeSlowSearchPid(pidSet);
        }
    }

    @Override
    public Set<String> getSlowSearchPidSet() {
        return slowSearchPidSet;
    }

    @Override
    public Integer getDownloadingCount() {
        return downloadFileSet.size();
    }

    /**
     * 定时清理进度中完成的任务
     */
    @Scheduled(cron = "0/3 * * * * *")
    public void cleanProgress() {
        synchronized (progressMain) {
            cleanProgress(progressMain);
        }
        synchronized (progressDownloading) {
            cleanProgress(progressDownloading);
        }
    }

    private void cleanProgress(Map<String, Map<String, Integer>> map) {
        Iterator<Map.Entry<String, Map<String, Integer>>> iterator = map.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Map<String, Integer>> next = iterator.next();
            Map<String, Integer> value = next.getValue();
            if (value.get("count").equals(value.get("size"))) {
                iterator.remove();
            }
        }
    }


    private void countTags() {
        List<Illustration> illList = new ArrayList<>(illustrationMap.values());
        for (Illustration ill : illList) {
            if (ill.getTagTranslated() != null) {
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

    /**
     * 生成唯一任务名称
     *
     * @param name 任务名
     * @return 唯一任务名
     */
    private static String getQuestName(String name) {
        return name + "-" + System.currentTimeMillis() % 1000;
    }
}

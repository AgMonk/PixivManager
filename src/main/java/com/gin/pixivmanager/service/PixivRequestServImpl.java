package com.gin.pixivmanager.service;

import com.alibaba.fastjson.JSONObject;
import com.gin.pixivmanager.config.PixivUrl;
import com.gin.pixivmanager.entity.Illustration;
import com.gin.pixivmanager.entity.Tag;
import com.gin.pixivmanager.util.PixivPost;
import com.gin.pixivmanager.util.Progress;
import com.gin.pixivmanager.util.ReqUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;

/**
 * @author bx002
 */
@Slf4j
@Service
public class PixivRequestServImpl implements PixivRequestServ {
    final ThreadPoolTaskExecutor downloadExecutor, requestExecutor, scanExecutor, downloadMainExecutor;
    final DataManager dataManager;
    final PixivUrl pixivUrl;
    final UserInfo userInfo;
    /**
     * 上次更新时间超过该时间的进行联网查询
     */
    final static long RANGE_OF_LAST_UPDATE = 30L * 24 * 60 * 60 * 1000;

    public PixivRequestServImpl(ThreadPoolTaskExecutor downloadExecutor,
                                ThreadPoolTaskExecutor requestExecutor,
                                ThreadPoolTaskExecutor scanExecutor,
                                ThreadPoolTaskExecutor downloadMainExecutor,
                                DataManager dataManager,
                                PixivUrl pixivUrl,
                                UserInfo userInfo) {
        this.downloadExecutor = downloadExecutor;
        this.requestExecutor = requestExecutor;
        this.scanExecutor = scanExecutor;
        this.downloadMainExecutor = downloadMainExecutor;
        this.dataManager = dataManager;
        this.pixivUrl = pixivUrl;
        this.userInfo = userInfo;


    }

    /**
     * 请求一个列表中的pid详情
     *
     * @param idSet pid
     * @return 作品详情
     */
    @Override
    public List<Illustration> getIllustrationDetail(Set<String> idSet) {
        List<Illustration> list = dataManager.getIllustrations(idSet);
        log.debug("从缓存中获得 {}条数据", list.size());
        Set<String> lackPidSet = new HashSet<>();
        //缓存中没有详情 或 更新时间过早的加入请求列表
        for (String s : idSet) {
            s = getPidFromFileName(s);
            Map<String, Illustration> map = dataManager.getIllustrationMap();
            //缓存没有
            if (!map.containsKey(s)) {
                log.debug("缓存中未查询到详情 {}", s);
                lackPidSet.add(s);
            } else {
                //距离时间过久的或没有的进行请求更新
                Long lastUpdate = map.get(s).getLastUpdate();
                long now = System.currentTimeMillis();
                if (lastUpdate == null || now - lastUpdate > RANGE_OF_LAST_UPDATE) {
                    log.debug("缓存中的详情记录过于久远 {}", s);
                    lackPidSet.add(s);
                }
            }
        }
        if (lackPidSet.size() > 0) {
            /*
              向pixiv查询到的作品详情
             */
            List<Illustration> detailsFromPixiv = getIllustrationFromPixiv(lackPidSet);
            if (detailsFromPixiv != null) {
                log.debug("向pixiv请求到 {} 条详情", detailsFromPixiv.size());
                list.addAll(detailsFromPixiv);
                dataManager.addIllustrations(detailsFromPixiv);
                dataManager.addTags(detailsFromPixiv);
            }
        }
        return list;
    }

    /**
     * 获取收藏的作品id
     *
     * @param tag  需要有的tag
     * @param page 最大获取数量
     * @return 收藏的作品id
     */
    @Override
    public Set<String> getBookmarks(String tag, Integer page) {
        Set<String> idSet = new HashSet<>();
        Progress progress = new Progress(getQuestName("扫描收藏"), page);
        dataManager.addMainProgress(progress);
        List<JSONObject> bookmarks = PixivPost.getBookmarks(userInfo.getCookie(), userInfo.getUid(), tag, page, scanExecutor, progress);
        progress.complete();
        if (bookmarks != null) {
            for (JSONObject bookmark : bookmarks) {
                idSet.add(bookmark.getString("id"));
            }
        }
        return idSet;
    }


    /**
     * 修改tag(批量)
     *
     * @param tag tag
     */
    @Override
    public void setTag(Tag tag) {
        String leftBrackets = "(";
        String leftChineseBrackets = "（";

        String translation = tag.getTranslation().replace(" ", "");

        if (translation.contains(leftBrackets)) {
            translation = translation.substring(0, translation.indexOf(leftBrackets));
        }
        if (translation.contains(leftChineseBrackets)) {
            translation = translation.substring(0, translation.indexOf(leftChineseBrackets));
        }
        tag.setTranslation(translation);

        requestExecutor.execute(() -> {
            PixivPost.setTag(userInfo.getCookie(), tag.getName(), tag.getTranslation(), userInfo.getTt());
        });


    }

    @Override
    public Set<String> archive(String[] name) {
        if (name == null || name.length == 0) {
            return null;
        }
        Set<String> idSet = new HashSet<>();
        for (String s : name) {
            String pid = getPidFromFileName(s);
            idSet.add(pid);
        }
        log.info("归档 {} 个文件 来自 {} 个作品", name.length, idSet.size());
//获得id的详情信息
        List<Illustration> detail = getIllustrationDetail(idSet);
        idSet = new HashSet<>();
        //归档目录
        String archivePath = userInfo.getArchivePath();
        //获得文件列表
        Map<String, File> filesMap = dataManager.getFilesMap(name);
        //文件总目录
        Map<String, File> map = dataManager.getFilesMap();

        for (Map.Entry<String, File> entry : filesMap.entrySet()) {
            String key = entry.getKey();
            File file = entry.getValue();
            String pid = getPidFromFileName(key);
            String destPath = archivePath;
            for (Illustration ill : detail) {
                if (ill.getId().equals(pid)) {
                    String count;
                    if (ill.getIllustType() == Illustration.ILLUST_TYPE_GIF) {
                        count = "0";
                    } else {
                        count = key.substring(key.indexOf("_p") + 2);
                    }
                    destPath += "/" + ill.createFormatName(dataManager.getTranslationMap()).replace("{count}", count);
                    break;
                }
            }
            if (destPath.equals(archivePath)) {
                destPath += "/fails/" + file.getName();
                log.error("无法获取作品详情 归档失败 {} 移动到 {}", key, destPath);
            }
            File dest = new File(destPath);

            //如果目标文件存在
            if (dest.exists()) {
                if (dest.length() == file.length()) {
                    map.remove(key);
                    idSet.add(key);
                    if (file.delete()) {
                        log.info("目标文件已存在 且大小相同 删除原文件 {}", key);
                    } else {
                        log.warn("目标文件已存在 且大小相同 删除原文件 失败");
                    }
                } else {
                    do {
                        String path = dest.getPath();
                        String newPath = path.substring(0, path.lastIndexOf("."));
                        String suffix = path.substring(path.lastIndexOf("."));
                        dest = new File(newPath + "_bak" + suffix);
                    } while (dest.exists());
                    log.info("目标文件已存在 且大小不同 请自行确认保留 {}", dest.getPath());
                }
            } else {
                File parentFile = dest.getParentFile();
                if (!parentFile.exists()) {
                    if (parentFile.mkdirs()) {
                        log.debug("创建文件夹 {}", parentFile.getPath());
                    } else {
                        log.warn("创建文件夹失败 {}", parentFile.getPath());
                    }
                }
                if (file.renameTo(dest)) {
                    log.debug("移动文件 {} 到 {}", file.getPath(), destPath);
                    map.remove(key);
                    idSet.add(key);

                    //如果目录已空 删除目录
                    File parent;
                    parent = file.getParentFile();
                    while (Objects.requireNonNull(parent.listFiles()).length == 0) {
                        if (parent.delete()) {
                            log.debug("删除目录 {}", parent);
                            parent = parent.getParentFile();
                        } else {
                            log.warn("删除目录失败 {}", parent);
                        }
                    }
                } else {
                    log.warn("移动失败 {} 到 {}", file.getPath(), destPath);
                }
            }
        }
        log.info("归档 {} 个文件 完成", idSet.size());

        return idSet;
    }

    @Override
    public List<File> downloadIllustAndAddTags(List<Illustration> illustList, String rootPath) {
        int size = illustList.size();
        log.info("请求下载 {}个作品", size);

        List<Callable<List<File>>> tasks = new ArrayList<>();
        for (Illustration ill : illustList) {
            tasks.add(new DownloadFilesTask(ill, rootPath, dataManager, downloadExecutor, userInfo.getCookie(), userInfo.getTt()));
        }

        List<List<File>> list = PixivPost.executeTasks(tasks, 590, downloadMainExecutor, "dMain", 5);

        List<File> files = new ArrayList<>();
        for (List<File> fileList : list) {
            files.addAll(fileList);
        }
        log.info("下载完成 {} 个作品 总计 {} 个文件", list.size(), files.size());

        return files;
    }

    /**
     * 如有 _ 截断 _
     *
     * @return 从文件名中获取的pid
     */
    private static String getPidFromFileName(String s) {
        return s.contains("_") ? s.substring(0, s.indexOf("_")) : s;

    }

    /**
     * 生成唯一任务名称
     *
     * @param name 任务名
     * @return 唯一任务名
     */
    private static String getQuestName(String name) {
        return name + System.currentTimeMillis() % 1000;
    }


    /**
     * 使用多线程请求多个作品详情
     *
     * @param pidSet 请求详情的id列表
     * @return 请求到的详情
     */
    private List<Illustration> getIllustrationFromPixiv(Set<String> pidSet) {
        //添加进度
        Progress progress = new Progress(getQuestName("详情任务"), pidSet.size());
        dataManager.addMainProgress(progress);

        List<JSONObject> detail = PixivPost.detail(pidSet, requestExecutor, progress);

        List<Illustration> illusts = new ArrayList<>();
        if (detail == null) {
            return null;
        }
        for (JSONObject json : detail) {
            illusts.add(new Illustration(json));
        }
        return illusts;
    }
}

/**
 * 下载单个作品的多个文件 并 添加tag任务
 */
@Slf4j
class DownloadFilesTask implements Callable<List<File>> {
    private final Illustration ill;
    private final String rootPath;
    private final DataManager dataManager;
    private final ThreadPoolTaskExecutor executor;
    private final String cookie;
    private final String tt;

    public DownloadFilesTask(Illustration ill, String rootPath, DataManager dataManager, ThreadPoolTaskExecutor executor, String cookie, String tt) {
        this.ill = ill;
        this.rootPath = rootPath;
        this.dataManager = dataManager;
        this.executor = executor;
        this.cookie = cookie;
        this.tt = tt;
    }

    @Override
    public List<File> call() throws Exception {
        String questName = ill.getId();
        List<String> urls = ill.getUrls();
        int size = urls.size();

        Progress progress = new Progress(questName, size);
        dataManager.addMainProgress(progress);
        List<Callable<File>> tasks = new ArrayList<>();
        for (String url : urls) {
            tasks.add(new DownloadFileTask(url, rootPath, progress));
        }
        List<File> files = PixivPost.executeTasks(tasks, 300, executor, "download", 5);

        //下载到的文件数量与url数量相同 则添加tag
        if (files.size() == size) {
            /*todo*/
            PixivPost.addTags(ill.getId(), ill.createSimpleTags(), cookie, tt);
        }
        return files;
    }
}

/**
 * 文件下载
 */
@Slf4j
class DownloadFileTask implements Callable<File> {
    //进度对象 传入之前 被存储在另一个对象中来显示一系列任务的总进度
    private final Progress progress;
    //下载根目录
    private final String rootPath;
    private final String url;

    public DownloadFileTask(String url, String rootPath, Progress progress) {
        this.progress = progress;
        this.rootPath = rootPath;
        this.url = url;
    }

    @Override
    public File call() throws Exception {
        String filePath = rootPath + "/" + url.substring(url.lastIndexOf("/") + 1);
        log.debug("开始下载 {} -> {}", url, filePath);
        File download = null;
        try {
            //下载文件(这是我自己包装的方法)
            download = ReqUtil.download(url, filePath);
            log.debug("下载完毕 {} -> {}", url, filePath);
            //下载完成 进度 +1
            progress.add(1);
        } catch (IOException e) {
            log.debug("下载失败 {} ", e.getMessage());
        }
        return download;
    }
}

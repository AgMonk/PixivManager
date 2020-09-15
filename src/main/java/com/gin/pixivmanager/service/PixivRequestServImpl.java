package com.gin.pixivmanager.service;

import com.alibaba.fastjson.JSONArray;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
     * @param idList pid
     * @return 作品详情
     */
    @Override
    public List<Illustration> getIllustrationDetail(List<String> idList) {
        List<Illustration> list = dataManager.getIllustrations(idList);
        log.debug("从缓存中获得 {}条数据", list.size());
        Set<String> lackPidSet = new HashSet<>();
        //缓存中没有详情 或 更新时间过早的加入请求列表
        for (String s : idList) {
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
            List<Illustration> detailsFromPixiv = getIllustrationDetail2List(lackPidSet);
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
     * 使用多线程请求多个作品详情
     *
     * @param pidSet 请求详情的id列表
     * @return 请求到的详情
     */
    private List<Illustration> getIllustrationDetail2List(Set<String> pidSet) {
        //添加进度
        Progress progress = new Progress(getQuestName("详情任务"), pidSet.size());
        dataManager.addDetailProgress(progress);

        List<JSONObject> detail = PixivPost.detail(pidSet, null, progress);

        List<Illustration> illusts = new ArrayList<>();
        if (detail == null) {
            return null;
        }
        for (JSONObject json : detail) {
            illusts.add(new Illustration(json));
        }
        return illusts;
    }

    /**
     * 获取收藏的作品id
     *
     * @param tag 需要有的tag
     * @param max 最大获取数量
     * @return 收藏的作品id
     */
    @Override
    public List<String> getBookmarks(String tag, Integer max) {
        long start = System.currentTimeMillis();
        log.info("获取收藏作品id tag:{}", tag);

        int offset = 0, limit = Math.min(10, max);

        List<String> idList = new ArrayList<>();
        String uid = userInfo.getUid();
        String rawUrl = pixivUrl.getBookmarks()
                .replace("{uid}", uid)
                .replace("{tag}", tag)
                .replace("{limit}", String.valueOf(limit));


        String result = ReqUtil.get(rawUrl + offset, null, null, null, userInfo.getCookie(), null, null, null);

        JSONObject body = JSONObject.parseObject(result).getJSONObject("body");

        Integer total = body.getInteger("total");
        log.info("{} 标签下有总计 {} 个作品", tag, total);
        total = Math.min(total, max);

        JSONArray works = body.getJSONArray("works");
        for (int i = 0; i < works.size(); i++) {
            JSONObject work = works.getJSONObject(i);
            idList.add(work.getString("id"));
        }

        if (total > limit) {
            int pages = total / limit + (total % limit != 0 ? 1 : 0);
            CountDownLatch latch = new CountDownLatch(pages - 1);
            for (int i = 1; i < pages; i++) {
                String url = rawUrl + i * limit;
                scanExecutor.execute(() -> {
                    idList.addAll(getBookmark(url));
                    latch.countDown();
                });
            }

            try {
                latch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        long end = System.currentTimeMillis();
        log.info("{} 标签获取完毕 总计数量 {}个 耗时{}秒", tag, total, (end - start) / 1000);
        return idList;

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

        String url = pixivUrl.getSetTag();
        Map<String, String> formData = new HashMap<>();
        String name = tag.getName();
        String translation = tag.getTranslation().replace(" ", "");

        if (translation.contains(leftBrackets)) {
            translation = translation.substring(0, translation.indexOf(leftBrackets));
        }
        if (translation.contains(leftChineseBrackets)) {
            translation = translation.substring(0, translation.indexOf(leftChineseBrackets));
        }

        formData.put("mode", "mod");
        formData.put("tag", name);
        formData.put("new_tag", translation);
        formData.put("tt", userInfo.getTt());

        log.info("修改tag {} -> {}", name, translation);

        scanExecutor.execute(() -> ReqUtil.post(url, null, null, null, userInfo.getCookie(), 5000, formData, null, 1, "utf-8"));

    }

    @Override
    public List<String> archive(String[] name) {
        if (name == null || name.length == 0) {
            return null;
        }
        List<String> idList = new ArrayList<>();
        for (String s : name) {
            String pid = getPidFromFileName(s);
            if (!idList.contains(pid)) {
                idList.add(pid);
            }
        }
        log.info("归档 {}个文件 来自 {}个作品", name.length, idList.size());
//获得id的详情信息
        List<Illustration> detail = getIllustrationDetail(idList);
        idList = new ArrayList<>();
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
                    idList.add(key);
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
                    log.info("目标文件已存在 且带大小不同 请自行确认保留 {}", dest.getPath());
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
                    idList.add(key);

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
        log.info("归档 {}个作品 完成", idList.size());

        return idList;
    }

    @Override
    public List<File> downloadIllustAndAddTags(List<Illustration> illustList, String rootPath) {
        List<File> outputFiles = new ArrayList<>();
        int size = illustList.size();
        log.info("下载 {}个作品", size);
        CountDownLatch latch = new CountDownLatch(size);

        for (Illustration ill : illustList) {
            downloadMainExecutor.execute(() -> {
                List<File> files = downloadIllustAndAddTags(ill, rootPath);
                outputFiles.addAll(files);
                dataManager.addFilesMap(files);
                latch.countDown();
            });
        }

        try {
            latch.await(9, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            log.warn("当前任务已持续9分钟 放弃剩余任务");
        }

        return outputFiles;
    }

    /**
     * 下载一个作品并添加tag
     *
     * @param ill      作品
     * @param rootPath 根目录
     * @return 下载好的文件（列表）
     */
    private List<File> downloadIllustAndAddTags(Illustration ill, String rootPath) {
        String questName = ill.getId();
        List<String> urls = ill.getUrls();
        List<File> fileList = new ArrayList<>();
        int size = urls.size();
        CountDownLatch latch = new CountDownLatch(size);
        AtomicBoolean error = new AtomicBoolean(true);


        for (String url : urls) {
            String filePath = rootPath + "/"
                    + url.substring(url.lastIndexOf("/") + 1);
            downloadExecutor.execute(() -> {
                try {
                    File download = download(url, filePath);
                    fileList.add(download);
                } catch (IOException e) {
                    //如果出错则不添加tag
                    log.error(e.getMessage());
                    error.set(false);
                }
                latch.countDown();

            });

        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (error.get()) {
            PixivPost.addTags(ill.getId(), ill.createSimpleTags(), userInfo.getCookie(), userInfo.getTt());
        }

        return fileList;
    }

    /**
     * 下载文件
     *
     * @param url 地址
     */
    private File download(String url, String filePath) throws IOException {
        log.debug("开始下载 {} -> {}", url, filePath);
        File download = ReqUtil.download(url, filePath);
        log.debug("下载完毕 {} -> {}", url, filePath);
        return download;
    }

    /**
     * 请求收藏中的作品ID
     *
     * @param url url
     */
    private List<String> getBookmark(String url) {
        List<String> list = new ArrayList<>();
        String result = ReqUtil.get(url, null, null, null, userInfo.getCookie(), null, null, null);
        JSONArray works = JSONObject.parseObject(result).getJSONObject("body").getJSONArray("works");
        for (int i = 0; i < works.size(); i++) {
            JSONObject work = works.getJSONObject(i);
            list.add(work.getString("id"));
        }
        return list;
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

}

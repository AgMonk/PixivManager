package com.gin.pixivmanager.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.gin.pixivmanager.config.PixivUrl;
import com.gin.pixivmanager.entity.Illustration;
import com.gin.pixivmanager.entity.Tag;
import com.gin.pixivmanager.util.ReqUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author bx002
 */
@Slf4j
@Service
public class PixivRequestServImpl implements PixivRequestServ {
    final Executor downloadExecutor, requestExecutor, scanExecutor, downloadMainExecutor;
    final DataManager dataManager;
    final PixivUrl pixivUrl;
    final UserInfo userInfo;

    public PixivRequestServImpl(Executor downloadExecutor, Executor requestExecutor, Executor scanExecutor, Executor downloadMainExecutor, DataManager dataManager, PixivUrl pixivUrl, UserInfo userInfo) {
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
        if (list.size() < idList.size()) {
            List<String> lackList = new ArrayList<>();
            for (String s : idList) {
                s = getPidFromFileName(s);
                Map<String, Illustration> map = dataManager.getIllustrationMap();
                if (!map.containsKey(s)) {
                    lackList.add(s);
                }
            }
            getIllustrationDetail2List(list, lackList);

            dataManager.addIllustrations(list);
            dataManager.addTags(list);
        }
        return list;
    }

    /**
     * 使用多线程请求多个作品详情 并放入一个指定list中
     *
     * @param list     目标list
     * @param lackList 请求详情的id列表
     */
    private void getIllustrationDetail2List(List<Illustration> list, List<String> lackList) {
        int size = lackList.size();

        long start = System.currentTimeMillis();
        String questName = "详情任务-" + start % 10000;

        log.info("查询作品详情 {}", size);
        CountDownLatch latch = new CountDownLatch(size);
        dataManager.addDetails(questName, latch.getCount(), size);
        for (String s : lackList) {
            requestExecutor.execute(() -> {
                list.add(getIllustrationDetail(s));
                latch.countDown();
                dataManager.addDetails(questName, latch.getCount(), size);
            });
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
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
        max = max != null ? max : total;
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
        log.info("获取完毕 总计数量 {}个 耗时{}秒", total, (end - start) / 1000);
        return idList;

    }

    /**
     * 批量添加tag
     *
     * @param list 详情列表
     */
    @Override
    public void addTags(List<Illustration> list) {
        int size = list.size();
        if (size == 0) {
            return;
        }

        long start = System.currentTimeMillis();
        String questName = "Tag添加任务-" + start % 1000;
        log.info("{} {}", questName, size);
        CountDownLatch latch = new CountDownLatch(size);

        for (Illustration ill : list) {
            requestExecutor.execute(() -> {
                addTags(ill);
                latch.countDown();
                dataManager.addDetails(questName, latch.getCount(), size);
            });
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        long end = System.currentTimeMillis();

        log.info("为 {} 件作品添加tag 完毕 总耗时 {} 秒", size, (end - start) / 1000);
    }

    /**
     * 修改tag(批量)
     *
     * @param tag tag
     */
    @Override
    public void setTag(Tag tag) {
        String url = pixivUrl.getSetTag();
        Map<String, String> formData = new HashMap<>();
        String name = tag.getName();
        String translation = tag.getTranslation().replace(" ", "");

        if (translation.contains("(")) {
            translation = translation.substring(0, translation.indexOf("("));
        }
        if (translation.contains("（")) {
            translation = translation.substring(0, translation.indexOf("（"));
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
        List<String> idList = new ArrayList<>();
        for (String s : name) {
            String pid = getPidFromFileName(s);
            if (!idList.contains(pid)) {
                idList.add(pid);
            }
        }
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
            File dest = new File(destPath);

            File parentFile = dest.getParentFile();
            if (!parentFile.exists()) {
                if (parentFile.mkdirs()) {
                    log.debug("创建文件夹 {}", parentFile.getPath());
                } else {
                    log.warn("创建文件夹失败 {}", parentFile.getPath());
                }
            }
            //如果目标文件存在
            if (dest.exists()) {
                if (dest.length() == file.length()) {
                    map.remove(key);
                    if (file.delete()) {
                        log.info("目标文件已存在 且大小相同 删除原文件");
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
                if (file.renameTo(dest)) {
                    log.debug("移动文件 {} 到 {}", file.getPath(), destPath);
                    map.remove(key);
                    idList.add(key);

                    //如果目录已空 删除目录
                    File parent = file.getParentFile();
                    if (parent.listFiles().length == 0) {
                        if (parent.delete()) {
                            log.debug("删除目录 {}", parent);
                        } else {
                            log.warn("删除目录失败 {}", parent);
                        }
                    }
                } else {
                    log.warn("移动失败 {} 到 {}", file.getPath(), destPath);
                }
            }
        }
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
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return outputFiles;
    }

    @Override
    public List<File> downloadIllustAndAddTags(String[] illustArray, String rootPath) {
        List<Illustration> detail = getIllustrationDetail(Arrays.asList(illustArray));
        return downloadIllustAndAddTags(detail, rootPath);
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
        dataManager.addDownloading(questName, latch.getCount(), size);

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
                dataManager.addDownloading(questName, latch.getCount(), size);

            });
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (error.get()) {
            addTags(ill);
        }

        return fileList;
    }

    /**
     * 为作品添加tag
     *
     * @param ill 作品
     */
    private void addTags(Illustration ill) {
        String id = ill.getId();
        String url = pixivUrl.getAddTags() + id;
        String tt = userInfo.getTt();
        String cookie = userInfo.getCookie();
        Map<String, String> dic = dataManager.getTranslationMap();
        String tag = ill.createSimpleTags(dic).replace(",", " ");

        Map<String, String> formData = new HashMap<>();
        formData.put("mode", "add");
        formData.put("type", "illust");
        formData.put("from_sid", "");
        formData.put("original_tag", "");
        formData.put("original_untagged", "0");
        formData.put("original_p", "1");
        formData.put("original_rest", "");
        formData.put("original_order", "");
        formData.put("comment", "");
        formData.put("restrict", "0");
        formData.put("tt", tt);
        formData.put("id", id);
        formData.put("tag", tag);

        log.info("{} 添加tag  {}", ill.getLink(), tag);

        ReqUtil.post(url, null, null, null, cookie, 5000, formData, null, 1, null);


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
     * @param id pid
     */
    private Illustration getIllustrationDetail(String id) {
        id = getPidFromFileName(id);
        Illustration illust = new Illustration();
        illust.setId(id);
        String url = pixivUrl.getIllustration().replace("{pid}", id);
        String s = ReqUtil.get(url, null, null, null);
        JSONObject result = JSONObject.parseObject(s);
        if (result != null && !result.getBoolean("error")) {
            illust = new Illustration(result.getJSONObject("body"));
        }

        return illust;
    }

    /**
     * 生成下载链接和目标文件地址
     *
     * @param illList 作品详情列表
     * @param rootDir 下载根目录
     * @return map
     */
    private Map<String, String> getUrlAndFilePath(List<Illustration> illList, String rootDir) {
        Map<String, String> map = new HashMap<>();
        illList.forEach(ill -> map.putAll(getUrlAndFilePath(ill, rootDir)));
        return map;
    }

    /**
     * 生成下载链接和目标文件地址
     *
     * @param ill     作品详情
     * @param rootDir 下载根目录
     * @return map
     */
    private Map<String, String> getUrlAndFilePath(Illustration ill, String rootDir) {
        Map<String, String> map = new HashMap<>();
        String urlPrefix = ill.getUrlPrefix();
        String fileName = ill.getFileName();
        rootDir += rootDir.endsWith("/") ? "" : "/";
        urlPrefix += urlPrefix.endsWith("/") ? "" : "/";
        Integer pageCount = ill.getPageCount();

        String url = urlPrefix + fileName;

        if (ill.getIllustType() == 2) {
            map.put(url, rootDir + ill.createSimpleName(0));
        } else {
            for (Integer i = 0; i < pageCount; i++) {
                String u = url.replace("_p0", "_p" + i);
                String n = rootDir + ill.createSimpleName(i);
                map.put(u, n);
            }
        }

        return map;
    }

    private static void printJson(Object obj) {
        System.err.println(prettyJson(obj));
    }

    private static String prettyJson(Object obj) {
        return JSON.toJSONString(obj, SerializerFeature.PrettyFormat, SerializerFeature.WriteMapNullValue,
                SerializerFeature.WriteDateUseDateFormat);
    }

    /**
     * 如有 _ 截断 _
     *
     * @return
     */
    private static String getPidFromFileName(String s) {
        return s.contains("_") ? s.substring(0, s.indexOf("_")) : s;

    }
}

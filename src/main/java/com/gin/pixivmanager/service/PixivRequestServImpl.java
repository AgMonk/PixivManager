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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;

/**
 * @author bx002
 */
@Slf4j
@Service
public class PixivRequestServImpl implements PixivRequestServ {
    final Executor downloadExecutor, requestExecutor, scanExecutor;
    final DataManager dataManager;
    final PixivUrl pixivUrl;
    final UserInfo userInfo;

    public PixivRequestServImpl(Executor downloadExecutor, Executor requestExecutor, Executor scanExecutor, DataManager dataManager, PixivUrl pixivUrl, UserInfo userInfo) {
        this.downloadExecutor = downloadExecutor;
        this.requestExecutor = requestExecutor;
        this.scanExecutor = scanExecutor;
        this.dataManager = dataManager;
        this.pixivUrl = pixivUrl;
        this.userInfo = userInfo;

    }

    /**
     * 下载多个文件
     *
     * @param detail  作品详情
     * @param rootDir 下载根目录
     */
    @Override
    public List<File> download(List<Illustration> detail, String rootDir) {
        if (detail.size() == 0) {
            return null;
        }

        long start = System.currentTimeMillis();
        Map<String, String> urlAndFilePath = getUrlAndFilePath(detail, rootDir);
        List<File> list = new ArrayList<>();

        int size = urlAndFilePath.size();
        String questName = "下载任务-" + start % 1000;

        log.info("{} 开始 {}", questName, size);
        
        CountDownLatch latch = new CountDownLatch(size);

        for (Map.Entry<String, String> entry : urlAndFilePath.entrySet()) {
            downloadExecutor.execute(() -> {
                list.add(download(entry.getKey(), entry.getValue(), latch, size, questName));
            });
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        long end = System.currentTimeMillis();

        log.info("{} 完毕 {} 耗时 {} 秒", questName, size, (end - start) / 1000);

        return list;
    }

    /**
     * 请求一个列表中的pid详情
     *
     * @param idList pid
     * @return 作品详情
     */
    @Override
    public List<Illustration> getIllustrationDetail(List<String> idList) {
        int size = idList.size();
        List<Illustration> list = new ArrayList<>(size);
        if (size > 0) {
            long start = System.currentTimeMillis();
            log.info("查询作品详情 {}", size);
            CountDownLatch latch = new CountDownLatch(size);
            for (String id : idList) {
                requestExecutor.execute(() -> {
                    list.add(getIllustrationDetail(id, latch, size, start));
                });
            }

            try {
                latch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            dataManager.addIllustrations(list);
            dataManager.addTags(list);
        }
        return list;
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

        Integer offset = 0, limit = 10;

        List<String> idList = new ArrayList<>();
        String uid = userInfo.getUid();
        String rawUrl = pixivUrl.getBookmarks()
                .replace("{uid}", uid)
                .replace("{tag}", tag)
                .replace("{limit}", limit.toString());

        String url = rawUrl + offset;

        String result = ReqUtil.get(url, null, null, null, userInfo.getCookie(), null, null, null);

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
                int finalI = i;
                scanExecutor.execute(() -> {
                    idList.addAll(getBookmark(rawUrl + finalI * limit, latch));
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
                addTags(ill, latch, size, questName);
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
     * @param tag
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

        scanExecutor.execute(() -> {
            ReqUtil.post(url, null, null, null, userInfo.getCookie(), 5000, formData, null, 1, "utf-8");
        });

    }

    /**
     * 为作品添加tag
     *
     * @param ill       作品
     * @param latch
     * @param size
     * @param questName
     */
    private void addTags(Illustration ill, CountDownLatch latch, int size, String questName) {
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

        latch.countDown();

        dataManager.addDetails(questName, latch.getCount(), size);
    }

    /**
     * 下载文件
     *
     * @param url
     * @param latch
     * @param size
     * @param questName 任务名称
     */
    private File download(String url, String filePath, CountDownLatch latch, int size, String questName) {
        File file = ReqUtil.download(url, filePath);
        if (latch != null) {
            latch.countDown();
        }
        dataManager.addDetails(questName, latch.getCount(), size);
        return file;
    }

    /**
     * 请求收藏中的作品ID
     *
     * @param url   url
     * @param latch 计数器
     */
    private List<String> getBookmark(String url, CountDownLatch latch) {
        List<String> list = new ArrayList<>();
        String result = ReqUtil.get(url, null, null, null, userInfo.getCookie(), null, null, null);
        JSONArray works = JSONObject.parseObject(result).getJSONObject("body").getJSONArray("works");
        for (int i = 0; i < works.size(); i++) {
            JSONObject work = works.getJSONObject(i);
            list.add(work.getString("id"));
        }
        if (latch != null) {
            latch.countDown();
        }
        return list;
    }

    /**
     * @param id    pid
     * @param latch 倒数计数器
     */
    private Illustration getIllustrationDetail(String id, CountDownLatch latch, Integer size, Long start) {
        Illustration illust = new Illustration();
        illust.setId(id);
        String url = pixivUrl.getIllustration().replace("{pid}", id);
        String s = ReqUtil.get(url, null, null, null);
        JSONObject result = JSONObject.parseObject(s);
        if (result != null && !result.getBoolean("error")) {
            illust = new Illustration(result.getJSONObject("body"));
        }
        if (latch != null) {
            latch.countDown();

            if (size != null && start != null) {
                String questName = "详情任务-" + start % 10000;
                dataManager.addDetails(questName, latch.getCount(), size);
            }
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
        String simpleName = rootDir + (rootDir.endsWith("/") ? "" : "/")
                + ill.createSimpleName(dataManager.getTranslationMap());
        Integer pageCount = ill.getPageCount();

        String url = urlPrefix + fileName;

        if (ill.getIllustType() == 2) {
            map.put(url, simpleName);
        } else {
            for (Integer i = 0; i < pageCount; i++) {
                String u = url.replace("_p0", "_p" + i);
                String n = simpleName.replace("_p0", "_p" + i);
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
}

package com.gin.pixivmanager.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.gin.pixivmanager.config.PixivUrl;
import com.gin.pixivmanager.entity.Illustration;
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

@Slf4j
@Service
public class PixivRequestServImpl implements PixivRequestServ {
    final Executor downloadExecutor, requestExecutor, scanExecutor;
    final DataManager dataManager;
    final PixivUrl pixivUrl;

    public PixivRequestServImpl(Executor downloadExecutor, Executor requestExecutor, Executor scanExecutor, DataManager dataManager, PixivUrl pixivUrl) {
        this.downloadExecutor = downloadExecutor;
        this.requestExecutor = requestExecutor;
        this.scanExecutor = scanExecutor;
        this.dataManager = dataManager;
        this.pixivUrl = pixivUrl;
    }

    /**
     * 下载多个文件
     *
     * @param idList  pid列表
     * @param rootDir 下载根目录
     */
    @Override
    public List<File> download(List<String> idList, String rootDir) {
        List<File> list = new ArrayList<>();
        List<Illustration> detail = getIllustrationDetail(idList);

        Map<String, String> urlAndFilePath = getUrlAndFilePath(detail, rootDir);

        CountDownLatch latch = new CountDownLatch(urlAndFilePath.size());

        for (Map.Entry<String, String> entry : urlAndFilePath.entrySet()) {
            download(entry.getKey(), entry.getValue(), list, latch);
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return list;
    }

    @Override
    public List<Illustration> getIllustrationDetail(List<String> idList) {
        int size = idList.size();
        long start = System.currentTimeMillis();
        log.info("查询作品详情 {}", size);
        CountDownLatch latch = new CountDownLatch(size);
        List<Illustration> list = new ArrayList<>();
        for (String id : idList) {
            getIllustrationDetail(id, list, latch, size, start);
        }
        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        dataManager.addIllustrations(list);
        dataManager.addTags(list);
        return list;
    }

    /**
     * 下载文件
     *
     * @param url
     * @param latch
     */
    private void download(String url, String filePath, List<File> files, CountDownLatch latch) {
        downloadExecutor.execute(() -> {
            File file = ReqUtil.download(url, filePath);
            if (files != null) {
                files.add(file);
            }
            if (latch != null) {
                latch.countDown();
            }
        });

    }

    /**
     * @param id    pid
     * @param list  接受结果的list
     * @param latch 倒数计数器
     */
    private void getIllustrationDetail(String id, List<Illustration> list, CountDownLatch latch, Integer size, Long start) {
        requestExecutor.execute(() -> {
            String url = pixivUrl.getIllustration().replace("{pid}", id);
            String s = ReqUtil.get(url, null, null, null);
            JSONObject result = JSONObject.parseObject(s);
            if (result != null && !result.getBoolean("error")) {
                Illustration illust = new Illustration(result.getJSONObject("body"));
                if (list != null) {
                    list.add(illust);
                }
            }
            if (latch != null) {
                latch.countDown();

                if (size != null && start != null) {
                    int count = Math.toIntExact(size - latch.getCount());
                    String key = "详情任务-" + start % 10000;
                    String value = count + "/" + size + " " + Math.floor(count * 1000.0 / size) / 10;
                    log.info(key + " " + value + " 耗时{}秒", (System.currentTimeMillis() - start) / 1000);
                    dataManager.addDetails(key, value);
                }
            }
        });
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

package com.gin.pixivmanager.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.gin.pixivmanager.config.PixivUrl;
import com.gin.pixivmanager.entity.Illustration;
import com.gin.pixivmanager.util.ReqUtil;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;

@Service
public class PixivRequestServImpl implements PixivRequestServ {
    final Executor downloadExecutor;
    final Executor requestExecutor;
    final Executor scanExecutor;
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
     * 下载文件
     *
     * @param url
     */
    @Override
    public void download(String url, String filePath) {
        downloadExecutor.execute(() -> {
            ReqUtil.download(url, filePath);
        });

    }

    /**
     * 下载文件
     *
     * @param urlAndFilePath key为url ,  value为filePath
     */
    @Override
    public void download(Map<String, String> urlAndFilePath) {
        for (Map.Entry<String, String> entry : urlAndFilePath.entrySet()) {
            download(entry.getKey(), entry.getValue());
        }
    }

    /**
     * @param id    pid
     * @param list  接受结果的list
     * @param latch 倒数计数器
     */
    @Override
    public void getIllustrationDetail(String id, List<Illustration> list, CountDownLatch latch) {
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
            }
        });
    }

    @Override
    public List<Illustration> getIllustrationDetail(List<String> idList) {
        CountDownLatch latch = new CountDownLatch(idList.size());
        List<Illustration> list = new ArrayList<>();
        for (String id : idList) {
            getIllustrationDetail(id, list, latch);
        }
        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return list;
    }


    private static void printJson(Object obj) {
        System.err.println(prettyJson(obj));
    }

    private static String prettyJson(Object obj) {
        return JSON.toJSONString(obj, SerializerFeature.PrettyFormat, SerializerFeature.WriteMapNullValue,
                SerializerFeature.WriteDateUseDateFormat);
    }
}

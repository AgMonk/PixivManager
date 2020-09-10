package com.gin.pixivmanager.util;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
/**
 * Nga发帖工具类
 *
 * @author bx002
 */
public class NgaPost {
    public final static String ACTION_NEW = "new";
    public final static String ACTION_REPLY = "reply";
    final static String URL_POST_PHP = "https://bbs.nga.cn/post.php";

    final String cookie, fid, tid, action;
    /**
     * 帖子正文
     */
    StringBuilder content = new StringBuilder();

    /**
     * 上传附件验证码
     */
    String auth;
    /**
     * 上传附件的接口地址
     */
    String attachUrl;
    /**
     * 附件
     */
    StringBuffer attachmentsBuffer = new StringBuffer();
    /**
     * 附件验证码
     */
    StringBuffer attachmentsCheckBuffer = new StringBuffer();
    /**
     * 已上传附件的 name 对应的url
     */
    Map<String, String> attachmentsMap = new HashMap<>();

    private NgaPost(String cookie, String fid, String tid, String action) {
        this.cookie = cookie;
        this.fid = fid;
        this.tid = tid;
        this.action = action;
    }

    public static NgaPost create(String cookie, String fid, String tid, String action) {
        NgaPost ngaPost = new NgaPost(cookie, fid, tid, action);

        return ngaPost;
    }

    /**
     * 请求attach_url和auth
     */
    private void getAuthAndAttachUrl() {
        StringBuilder urlbuilder = new StringBuilder(URL_POST_PHP);
        urlbuilder
                .append("?action=").append(action).append("&")
                .append("fid=").append(fid).append("&")
        ;
        if (tid != null && !"".equals(tid)) {
            urlbuilder.append("tid=").append(tid).append("&");
        }
        urlbuilder.append("__output=14").append("&");

        String post = ReqUtil.post(urlbuilder.toString(), null
                , null, null, cookie, null, null, null, null, "gbk");

        JSONObject json = JSONObject.parseObject(post);
        Integer code = json.getInteger("code");

        while (code == 39) {
            log.debug("发言CD中");
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            post = ReqUtil.post(urlbuilder.toString(), null
                    , null, null, cookie, null, null, null, null, "gbk");
            json = JSONObject.parseObject(post);
            code = json.getInteger("code");
        }
        JSONObject result = json.getJSONArray("result").getJSONObject(0);
        attachUrl = result.getString("attach_url");
        auth = result.getString("auth");
    }

    /**
     * 上传文件
     *
     * @param i    序号
     * @param file 文件
     * @param name 与上传后的url唯一对应的名称
     */
    private void uploadFile(int i, String name, File file) {
        HashMap<String, String> formData = new HashMap<>();
        formData.put("attachment_file" + i + "_watermark", "");
        formData.put("attachment_file" + i + "_dscp", "image" + i);
        formData.put("attachment_file" + i + "_url_utf8_name", ReqUtil.encode(file.getName(), "utf-8"));
        formData.put("attachment_file" + i + "_img", "1");
        formData.put("func", "upload");
        formData.put("v2", "1");
        formData.put("auth", auth);
        formData.put("origin_domain", "bbs.nga.cn");
        formData.put("fid", fid);
        formData.put("__output", "8");
        //大于4M自动压缩
        int maxLength = 4;
        int k = 1024;
        if (file.length() >= maxLength * k * k) {
            formData.put("attachment_file" + i + "_auto_size", "1");
        } else {
            formData.put("attachment_file" + i + "_auto_size", "0");
        }

        //放入文件
        HashMap<String, File> fileMap = new HashMap<>(1);
        fileMap.put("attachment_file" + i, file);

        String result = ReqUtil.post(attachUrl, "", null,
                null, null, null, formData, fileMap,
                null, "gbk");
        JSONObject data = JSONObject.parseObject(result).getJSONObject("data");

        String attachments = data.getString("attachments");
        String attachmentsCheck = data.getString("attachments_check");
        String url = data.getString("url");

        //保存 附件 验证码 url
        attachmentsBuffer.append(attachments).append("\t");
        attachmentsCheckBuffer.append(attachmentsCheck).append("\t");
        attachmentsMap.put(name, url);
    }


    /**
     * 多线程上传文件
     *
     * @param map 标识符 - 文件
     */
    public void uploadFiles(Map<String, File> map) {
        getAuthAndAttachUrl();

        CountDownLatch latch = new CountDownLatch(map.size());
        Executor executor = getExecutor();
        //一次上传多个文件
        int i = 0;
        for (Map.Entry<String, File> entry : map.entrySet()) {
            int finalI = i;
            executor.execute(() -> {
                uploadFile(finalI, entry.getKey(), entry.getValue());
                latch.countDown();
            });
            i++;
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


    }


    /**
     * 上传线程池
     *
     * @return 线程池
     */
    private Executor getExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        //核心线程池大小
        executor.setCorePoolSize(5);
        //最大线程数
        executor.setMaxPoolSize(5);
        //队列容量
        executor.setQueueCapacity(10);
        //活跃时间
        executor.setKeepAliveSeconds(300);
        //线程名字前缀
        executor.setThreadNamePrefix("upload-");

        // setRejectedExecutionHandler：当pool已经达到max size的时候，如何处理新任务
        // CallerRunsPolicy：不在新线程中执行任务，而是由调用者所在的线程来执行
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 等待所有任务结束后再关闭线程池
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }

}

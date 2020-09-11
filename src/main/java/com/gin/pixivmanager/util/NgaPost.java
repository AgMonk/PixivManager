package com.gin.pixivmanager.util;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
/**
 * Nga发帖工具类
 *
 * @author bx002
 */
public class NgaPost {
    final static String NBSP = "\r\n";
    public final static String ACTION_NEW = "new";
    public final static String ACTION_REPLY = "reply";
    final static String NGA_ROOT_URL = "https://bbs.nga.cn";
    final static String NGA_POST_URL = NGA_ROOT_URL + "/post.php";
    final static String NGA_READ_URL = NGA_ROOT_URL + "/read.php";
    static String BBS_CODE_TAG_IMG = "[img]./{url}[/img]";
    static String BBS_CODE_TAG_FLASH = "[flash=video]./{url}[/flash]";
    static String BBS_CODE_TAG_COLLAPSE = "[collapse={title}]{content}[/collapse]";
    static String BBS_CODE_TAG_QUOTE = "[quote]" + NBSP + "{content}" + NBSP + "[/quote]";

    final static Pattern PATTERN_PID = Pattern.compile("pid\\d+");
    final static Pattern PATTERN_TID = Pattern.compile("tid=\\d+");
    /**
     * 发帖账号的cookie
     */
    final String cookie;
    /**
     * 发帖版面id
     */
    final String fid;
    /**
     * 回复主题id
     */
    final String tid;
    /**
     * 动作 new = 发帖  reply = 回复
     */
    final String action;
    /**
     * 帖子正文
     */
    StringBuilder contentBuilder = new StringBuilder();
    /**
     * 帖子标题
     */
    StringBuilder titleBuilder = new StringBuilder();

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

    public static NgaPost create(String cookie, String fid, String tid, String action) {
        return new NgaPost(cookie, fid, tid, action);
    }

    /**
     * 发帖
     *
     * @return 发帖/回复的地址
     */
    public String send() {
        Map<String, String[]> paramMap = new HashMap<>();
        paramMap.put("action", new String[]{action});
        paramMap.put("fid", new String[]{fid});
        paramMap.put("lite", new String[]{"htmljs"});
        paramMap.put("step", new String[]{"2"});
        paramMap.put("post_subject", new String[]{titleBuilder.toString()});
        paramMap.put("post_content", new String[]{contentBuilder.toString()});
        paramMap.put("tpic_misc_bit1", new String[]{"40"});
        paramMap.put("attachments", new String[]{attachmentsBuffer.toString()});
        paramMap.put("attachments_check", new String[]{attachmentsCheckBuffer.toString()});

        if (action.equals(ACTION_REPLY)) {
            paramMap.put("tid", new String[]{tid});
        }
        String post = ReqUtil.post(NGA_POST_URL, null, null, paramMap, cookie,
                null, null, null, 2, "gbk");
        String url = null;
        if (post.contains("发贴完毕")) {
            if (action.equals(ACTION_REPLY)) {
                Matcher matcher = PATTERN_PID.matcher(post);
                if (matcher.find()) {
                    String pid = matcher.group().substring(3);
                    url = NGA_READ_URL + "?pid=" + pid;
                }
            }
            if (action.equals(ACTION_NEW)) {
                Matcher matcher = PATTERN_TID.matcher(post);
                if (matcher.find()) {
                    String tid = matcher.group();
                    url = NGA_READ_URL + "?" + tid;
                }
            }
        }

        return url;
    }

    /**
     * 多线程上传文件
     *
     * @param map 标识符 - 文件
     * @return
     */
    public Set<String> uploadFiles(Map<String, File> map) {
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

        return attachmentsMap.keySet();
    }

    /**
     * 插入附件
     *
     * @param name
     */
    public NgaPost addAttachment(String name) {
        addContent(getAttachmentCode(name));
        return this;
    }

    /**
     * 换行
     */
    public NgaPost addWrap() {
        addContent(getWrap());
        return this;
    }

    /**
     * 换行
     *
     * @return
     */
    public String getWrap() {
        return NBSP;
    }

    /**
     * 添加正文内容
     *
     * @return this
     */
    public NgaPost addContent(String s) {
        contentBuilder.append(s);
        return this;
    }

    /**
     * 添加标题内容
     *
     * @return this
     */
    public NgaPost addTitle(String s) {
        titleBuilder.append(s);
        return this;
    }

    /**
     * 添加折叠
     *
     * @param title   标题
     * @param content 内容
     * @return this
     */
    public NgaPost addCollapse(String title, String content) {
        addContent(getCollapse(title, content));
        return this;
    }

    /**
     * 获得附件的bbscode
     *
     * @param name 附件名
     * @return bbscode
     */
    public String getAttachmentCode(String name) {
        String url = attachmentsMap.get(name);
        if (url.contains("mp4")) {
            return BBS_CODE_TAG_FLASH.replace("{url}", url);
        } else {
            return BBS_CODE_TAG_IMG.replace("{url}", url);
        }
    }

    /**
     * 获得折叠的bbscode
     *
     * @param title   标题
     * @param content 内容
     * @return code
     */
    public static String getCollapse(String title, String content) {
        String code = BBS_CODE_TAG_COLLAPSE;
        if (title == null || "".equals(title)) {
            code = code.replace("={title}", "");
        } else {
            code = code.replace("{title}", delKorea(title));
        }
        code = code.replace("{content}", content);

        return code;
    }

    /**
     * 获得引用的bbscode
     *
     * @param content 内容
     * @return code
     */
    public static String getQuote(String content) {
        return BBS_CODE_TAG_QUOTE.replace("{content}", content);
    }


    /**
     * 获得链接的bbscode
     *
     * @param title 标题
     * @param url   链接
     * @return code
     */
    public static String getUrlCode(String title, String url) {
        return "[url=" + url + "]" + title + "[/url]";
    }


    /**
     * 请求attach_url和auth
     */
    private void getAuthAndAttachUrl() {
        StringBuilder urlbuilder = new StringBuilder(NGA_POST_URL);
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
        int largeLength = 12;
        int k = 1024;
        HashMap<String, File> fileMap = null;
        if (file.length() >= largeLength * k * k) {
            log.warn("文件过大>12M");
            /*todo 把文件转换为base64字符串*/
            return ;
//            formData.put("attachment_base64_file" + i, encryptToBase64(file.getPath()));
        } else {
            if (file.length() >= maxLength * k * k) {
                formData.put("attachment_file" + i + "_auto_size", "1");
            } else {
                formData.put("attachment_file" + i + "_auto_size", "0");
            }
            //放入文件
            fileMap = new HashMap<>(1);
            fileMap.put("attachment_file" + i, file);
        }

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

    private NgaPost(String cookie, String fid, String tid, String action) {
        this.cookie = cookie;
        this.fid = fid;
        this.tid = tid;
        this.action = action;
    }

    public StringBuilder getContentBuilder() {
        return contentBuilder;
    }

    public StringBuilder getTitleBuilder() {
        return titleBuilder;
    }

    /**
     * 删除韩文
     *
     * @param str
     * @return
     */
    private static String delKorea(String str) {
        StringBuilder unicode = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            // 取出每一个字符
            char c = str.charAt(i);
            // 韩文转换为unicode
            if (!(c >= 44032 && c <= 55215)) {
                unicode.append(c);
            }
        }
        return unicode.toString();
    }

    /**
     * 把文件转换为base64
     *
     * @param imgPath
     * @return
     */
    public static String encryptToBase64(String imgPath)  {
        byte[] data = null;
        // 读取图片字节数组
        try {
            InputStream in = new FileInputStream(imgPath);
            System.out.println("文件大小（字节）="+in.available());
            data = new byte[in.available()];
            in.read(data);
            in.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        // 对字节数组进行Base64编码，得到Base64编码的字符串
        Base64.Encoder encoder = Base64.getEncoder();
        String base64Str = encoder.encodeToString(data);
        return base64Str;
    }

    public static void main(String[] args) throws IOException {
        String s = encryptToBase64("F:\\[pixiv]\\未分類\\84303763_p0.jpg");
        System.err.println(s);
    }
}

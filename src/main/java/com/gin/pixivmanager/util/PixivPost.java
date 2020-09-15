package com.gin.pixivmanager.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.*;
import java.util.concurrent.*;

/**
 * Pixiv请求工具类
 *
 * @author bx002
 */
public class PixivPost {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PixivPost.class);
    /**
     * 作品详情接口
     */
    final static String URL_ILLUST_DETAIL = "https://www.pixiv.net/ajax/illust/{pid}";
    /**
     * 获取收藏作品接口 需要cookie
     */
    final static String URL_BOOKMARKS_GET = "https://www.pixiv.net/ajax/user/{uid}/illusts/bookmarks?limit={limit}&rest=show&lang=zh&offset={offset}&tag={tag}";
    /**
     * 给单个作品添加tag接口 需要cookie
     */
    final static String URL_TAG_ADD = "https://www.pixiv.net/bookmark_add.php?id={pid}";
    /**
     * 批量修改tag名称接口 需要cookie
     */
    final static String URL_TAG_SET = "https://www.pixiv.net/bookmark_tag_setting.php";
    /**
     * 搜索作品接口
     */
    final static String URL_ILLUST_SEARCH = "https://www.pixiv.net/ajax/search/artworks/{keyword}";
    /**
     * 搜索用户作品接口
     */
    final static String URL_USER_ILLUST = "https://www.pixiv.net/ajax/user/{uid}/profile/all";
    /**
     * 添加收藏接口 需要cookie
     */
    final static String URL_BOOKMARKS_ADD = "https://www.pixiv.net/rpc/index.php";

    /**
     * 请求地址
     */
    String url;
    /**
     * 响应字符串
     */
    String result = null;
    /**
     * cookie
     */
    String cookie = null;
    /**
     * formData
     */
    Map<String, String> formData = null;
    /**
     * 地址栏参数map
     */
    Map<String, String> paramMap = null;
    /**
     * 超时时间
     */
    Integer timeout;
    /**
     * 最大尝试次数
     */
    Integer maxTimes;


    /*—————————— 公开方法 ——————————*/

    /**
     * 查询作品详情
     *
     * @param pid pid
     * @return 如无错误 返回body对象 否则为null
     */
    public static JSONObject detail(String pid) {
        PixivPost post = new PixivPost(URL_ILLUST_DETAIL);

        long start = System.currentTimeMillis();
        log.info("请求作品详情 {}", pid);

        JSONObject body = post.addParamMap("pid", pid)
                .sendGet()
                .getBody(pid);
        long end = System.currentTimeMillis();
        log.info("获得作品详情 {} 用时 {} 毫秒", pid, end - start);
        return body;
    }

    public static List<JSONObject> detail(Set<String> pid, ThreadPoolTaskExecutor executor, Progress progress) {
        List<Callable<JSONObject>> tasks = new ArrayList<>();
        for (String s : pid) {
            tasks.add(new detailTask(s, progress));
        }
        return executeTasks(tasks, 60, executor);
    }


    /**
     * 为一个作品添加tag
     *
     * @param pid    pid
     * @param tag    tag
     * @param cookie pixiv cookie
     * @param tt     tt
     */
    public static void addTags(String pid, String tag, String cookie, String tt) {
        PixivPost post = new PixivPost(URL_TAG_ADD);
        tag = tag.replace(",", " ");
        log.info("给作品添加tag {} -> {}", pid, tag);
        post.addParamMap("pid", pid)
                .setCookie(cookie)
                .addFormData("tt", tt)
                .addFormData("id", pid)
                .addFormData("tag", tag)
                .addFormData("mode", "add")
                .addFormData("type", "illust")
                .addFormData("from_sid", "")
                .addFormData("original_tag", "")
                .addFormData("original_untagged", "0")
                .addFormData("original_p", "1")
                .addFormData("original_rest", "")
                .addFormData("original_order", "")
                .addFormData("comment", "")
                .addFormData("restrict", "0")
                .setMaxTimes(1)
                .setTimeout(3000)
                .sendPost()
        ;
    }

    /**
     * 请求收藏中作品
     *
     * @param cookie cookie
     * @param uid    uid
     * @param limit  limit
     * @param offset offset
     * @param tag    tag
     * @return body
     */
    public static JSONObject getBookmarks(String cookie, String uid, String tag, int limit, int offset) {
        long start = System.currentTimeMillis();
        PixivPost post = new PixivPost(URL_BOOKMARKS_GET);
        log.info("请求收藏 UID={} 标签={} 第 {} 页", uid, tag, offset / limit + 1);
        JSONObject body = post.setCookie(cookie)
                .addParamMap("uid", uid)
                .addParamMap("limit", String.valueOf(limit))
                .addParamMap("offset", String.valueOf(offset))
                .addParamMap("tag", tag)
                .sendGet()
                .getBody("请求收藏 " + uid);
        if (body != null) {
            log.info("获得收藏 UID={} 标签={} 第 {} 页 耗时 {} 毫秒", uid, tag, offset / limit + 1, System.currentTimeMillis() - start);
        } else {
            log.warn("请求错误 UID={} 标签={} 第 {} 页", uid, tag, offset / limit + 1);
        }
        return body;
    }

    /**
     * 请求收藏中的作品
     *
     * @param cookie cookie
     * @param uid    uid
     * @param tag    tag
     * @param page   页数
     * @return 作品列表
     */
    public static List<JSONObject> getBookmarks(String cookie, String uid, String tag, Integer page, ThreadPoolTaskExecutor executor, Progress progress) {
        long start = System.currentTimeMillis();
        page = (page == null || page < 1) ? 1 : page;
        int offset = 0;
        int limit = 10;
        //请求到的数量
        int total = 0;
        List<JSONObject> worksList = null;
        JSONObject body = getBookmarks(cookie, uid, tag, limit, offset);
        progress.add(1);
        if (body != null) {
            total = body.getInteger("total");
            log.info("{} 标签下有总计 {} 个作品", tag, total);
            total = Math.min(total, page * limit);
            log.info("请求 {} 个作品", total);
            JSONArray works = body.getJSONArray("works");
            for (int i = 0; i < works.size(); i++) {
                worksList = worksList != null ? worksList : new ArrayList<>();
                worksList.add(works.getJSONObject(i));
            }
        }
        offset += limit;

        if (offset < total) {
            List<Callable<JSONObject>> tasks = new ArrayList<>();
            while (offset < total) {
                tasks.add(new bookmarkTask(cookie, uid, tag, limit, offset, progress));
                offset += limit;
            }
            List<JSONObject> otherBodies = executeTasks(tasks, 60, executor);
            for (JSONObject otherBody : otherBodies) {
                JSONArray works = otherBody.getJSONArray("works");
                for (int i = 0; i < works.size(); i++) {
                    worksList = worksList != null ? worksList : new ArrayList<>();
                    worksList.add(works.getJSONObject(i));
                }
            }
        }
        log.info("获取 {} 个作品 耗时 {} 毫秒", total, System.currentTimeMillis() - start);
        return worksList;
    }










    /*—————————— 基础方法 ————————————*/

    /**
     * 判断请求是否成功 如果成功返回body json对象
     *
     * @param msg 自定义错误消息内容
     */
    private JSONObject getBody(String msg) {
        if (result != null) {
            JSONObject json = JSONObject.parseObject(result);
            String error = "error";
            if (json.getBoolean(error)) {
                //出错
                String message = json.getString("message");
                log.error(msg + " " + message);
            } else {
                return json.getJSONObject("body");
            }
        }
        return null;
    }

    /**
     * 线程池
     *
     * @return 线程池
     */
    private static ThreadPoolTaskExecutor getExecutor(int size, String name) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        //核心线程池大小
        executor.setCorePoolSize(size);
        //最大线程数
        executor.setMaxPoolSize(size);
        //队列容量
        executor.setQueueCapacity(1000);
        //活跃时间
        executor.setKeepAliveSeconds(300);
        //线程名字前缀
        executor.setThreadNamePrefix("P-" + name + "-");
        // setRejectedExecutionHandler：当pool已经达到max size的时候，如何处理新任务
        // CallerRunsPolicy：不在新线程中执行任务，而是由调用者所在的线程来执行
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 等待所有任务结束后再关闭线程池
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();

        return executor;
    }

    private PixivPost(String rawUrl) {
        this.url = rawUrl;
    }

    /**
     * 执行post请求
     *
     * @return this
     */
    private PixivPost sendPost() {
        createUrl();
        result = ReqUtil.post(url, null, null, null, cookie, timeout, formData, null, maxTimes, "utf-8");
        return this;
    }

    /**
     * 执行get请求
     *
     * @return this
     */
    private PixivPost sendGet() {
        createUrl();
        result = ReqUtil.get(url, null, null, null, cookie, timeout, maxTimes, "utf-8");
        return this;
    }


    /**
     * 替换基础url中的param参数
     */
    private void createUrl() {
        for (Map.Entry<String, String> entry : paramMap.entrySet()) {
            url = url.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        log.debug("请求地址 {}", url);
    }

    /**
     * 添加 param 参数
     *
     * @param k key
     * @param v value
     * @return this
     */
    private PixivPost addParamMap(String k, String v) {
        paramMap = paramMap != null ? paramMap : new HashMap<>();
        log.debug("参数 {} -> {}", k, v);
        paramMap.put(k, v);
        return this;
    }

    /**
     * 添加 formData数据
     *
     * @param k key
     * @param v value
     * @return this
     */
    private PixivPost addFormData(String k, String v) {
        formData = formData != null ? formData : new HashMap<>();
        if (v != null && !"".equals(v)) {
            log.debug("formData {} -> {}", k, v);
        }
        formData.put(k, v);
        return this;
    }

    private static void printJson(Object obj) {
        System.err.println(prettyJson(obj));
    }

    private static String prettyJson(Object obj) {
        return JSON.toJSONString(obj, SerializerFeature.PrettyFormat, SerializerFeature.WriteMapNullValue,
                SerializerFeature.WriteDateUseDateFormat);
    }

    public PixivPost setCookie(String cookie) {
        this.cookie = cookie;
        return this;
    }

    public String getResult() {
        return result;
    }


    /**
     * 执行多个任务
     *
     * @param tasks          任务集合
     * @param timeoutSeconds 单个任务的超时时间(秒)
     * @param executor       指定线程池 否则使用自带线程池
     * @param taskName       自带线程池名称
     * @param defaultSize    自带线程池size
     * @param <T>            返回类型
     * @return 结果列表
     */
    public static <T> List<T> executeTasks(Collection<Callable<T>> tasks, Integer timeoutSeconds, ThreadPoolTaskExecutor executor, String taskName, Integer defaultSize) {
        //是否使用自己的线程池
        boolean b = executor == null;
        if (b) {
            log.info("使用自身线程池");
            executor = getExecutor(defaultSize, taskName);
        }

        List<Future<T>> futures = new ArrayList<>();
        List<T> results = null;
        for (Callable<T> task : tasks) {
            Future<T> future = executor.submit(task);
            futures.add(future);
        }

        for (Future<T> future : futures) {
            try {
                T json = future.get(timeoutSeconds, TimeUnit.SECONDS);
                results = results == null ? new ArrayList<>() : results;
                results.add(json);

            } catch (ExecutionException | TimeoutException e) {
                future.cancel(true);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        if (b) {
            executor.shutdown();
        }
        return results;
    }

    private static <T> List<T> executeTasks(Collection<Callable<T>> tasks, Integer timeoutSeconds, ThreadPoolTaskExecutor executor) {
        return executeTasks(tasks, timeoutSeconds, executor, null, null);
    }

    private static <T> List<T> executeTasks(Collection<Callable<T>> tasks, Integer timeoutSeconds, String taskName, Integer defaultSize) {
        return executeTasks(tasks, timeoutSeconds, null, taskName, defaultSize);

    }

    public PixivPost setTimeout(Integer timeout) {
        this.timeout = timeout;
        return this;
    }

    public PixivPost setMaxTimes(Integer maxTimes) {
        this.maxTimes = maxTimes;
        return this;
    }

    public static void main(String[] args) {
        String[] pid = new String[]{"84385635", "84385614", "84385600"};
        Set<String> pidSet = new HashSet<>(Arrays.asList(pid));


        log.info("执行完毕");
    }
}

/**
 * 详情任务
 */
class detailTask implements Callable<JSONObject> {
    private final String pid;
    private final Progress progress;

    public detailTask(String pid, Progress progress) {
        this.pid = pid;
        this.progress = progress;
    }

    @Override
    public JSONObject call() throws Exception {
        JSONObject detail = PixivPost.detail(pid);
        if (progress != null) {
            progress.add(1);
        }
        return detail;
    }
}

/**
 * 请求收藏中作品任务
 */
class bookmarkTask implements Callable<JSONObject> {
    private final String cookie;
    private final String uid;
    private final String tag;
    private final int limit;
    private final int offset;
    private final Progress progress;

    bookmarkTask(String cookie, String uid, String tag, int limit, int offset, Progress progress) {
        this.cookie = cookie;
        this.uid = uid;
        this.tag = tag;
        this.limit = limit;
        this.offset = offset;
        this.progress = progress;
    }

    /**
     * Computes a result, or throws an exception if unable to do so.
     *
     * @return computed result
     * @throws Exception if unable to compute a result
     */
    @Override
    public JSONObject call() throws Exception {
        JSONObject bookmarks = PixivPost.getBookmarks(cookie, uid, tag, limit, offset);
        if (progress != null) {
            progress.add(1);
        }
        return bookmarks;
    }
}
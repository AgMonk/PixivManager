package com.gin.pixivmanager.util;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
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
     * 作品详情接口(cookie可选)
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
     * 搜索作品接口(cookie可选)
     */
    final static String URL_ILLUST_SEARCH = "https://www.pixiv.net/ajax/search/artworks/{keyword}?p={p}&s_mode={s_mode}&mode={mode}";
    /**
     * 添加收藏接口 需要cookie
     */
    final static String URL_BOOKMARKS_ADD = "https://www.pixiv.net/rpc/index.php";
    /**
     * 搜索用户作品接口
     */
    final static String URL_USER_ILLUST = "https://www.pixiv.net/ajax/user/{uid}/profile/all";

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
     * @param pid    pid
     * @param cookie
     * @return 如无错误 返回body对象 否则为null
     */
    public static JSONObject detail(String pid, String cookie) {

        long start = System.currentTimeMillis();
        log.info("请求作品详情{} {}", cookie == null ? "" : "(cookie)", pid);

        JSONObject body = create(URL_ILLUST_DETAIL).addParamMap("pid", pid)
                .setCookie(cookie)
                .sendGet()
                .getBody(pid);
        long end = System.currentTimeMillis();
        log.info("作品详情获取{} {} 用时 {} 毫秒", body != null ? "成功" : "失败", pid, end - start);
        return body;
    }

    /**
     * 批量查询详情
     *
     * @param pidSet   pid集合
     * @param cookie   cookie
     * @param executor 线程池
     * @param progress 进度对象
     * @return 详情列表
     */
    public static List<JSONObject> detail(Set<String> pidSet, String cookie, ThreadPoolTaskExecutor executor, Progress progress) {
        List<Callable<JSONObject>> tasks = new ArrayList<>();
        for (String pid : pidSet) {
            tasks.add(() -> {
                JSONObject detail = PixivPost.detail(pid, cookie);
                if (progress != null) {
                    progress.add(1);
                }
                return detail;
            });
        }
        return executeTasks(tasks, 60, executor, "detail", 2);
    }

    /**
     * 为一个作品添加tag
     *
     * @param pid    pid
     * @param tags   tags
     * @param cookie pixiv cookie
     * @param tt     tt
     */
    public static void addTags(String pid, String cookie, String tt, String tags) {
        tags = tags.replace(",", " ");
        log.info("给作品添加tag {} -> {}", pid, tags);
        create(URL_TAG_ADD).addParamMap("pid", pid)
                .setCookie(cookie)
                .addFormData("tt", tt)
                .addFormData("id", pid)
                .addFormData("tag", tags)
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
    public static JSONObject getBookmarks(String uid, String cookie, String tag, int limit, int offset) {
        long start = System.currentTimeMillis();
        log.debug("请求收藏 UID={} 标签={} 第 {} 页", uid, tag, offset / limit + 1);
        JSONObject body = create(URL_BOOKMARKS_GET).setCookie(cookie)
                .addParamMap("uid", uid)
                .addParamMap("limit", String.valueOf(limit))
                .addParamMap("offset", String.valueOf(offset))
                .addParamMap("tag", tag)
                .sendGet()
                .getBody("请求收藏 " + uid);
        if (body != null) {
            log.debug("获得收藏 UID={} 标签={} 第 {} 页 耗时 {} 毫秒", uid, tag, offset / limit + 1, System.currentTimeMillis() - start);
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
    public static List<JSONObject> getBookmarks(String uid, String cookie, String tag, Integer page, ThreadPoolTaskExecutor executor, Progress progress) {
        long start = System.currentTimeMillis();
        page = (page == null || page < 1) ? 1 : page;
        int offset = 0;
        int limit = 10;
        //请求到的数量
        int total = 0;
        List<JSONObject> worksList = null;
        JSONObject body = getBookmarks(uid, cookie, tag, limit, offset);
        progress.add(1);
        if (body != null) {
            total = body.getInteger("total");
            log.info("{} 标签下有总计 {} 个作品", tag, total);
            total = Math.min(total, page * limit);
            log.debug("请求 {} 个作品", total);
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
                int finalOffset = offset;
                tasks.add(() -> {
                    JSONObject bookmarks = PixivPost.getBookmarks(uid, cookie, tag, limit, finalOffset);
                    progress.add(1);
                    return bookmarks;
                });
                offset += limit;
            }
            List<JSONObject> otherBodies = executeTasks(tasks, 60, executor, "bookmark", 2);
            for (JSONObject otherBody : otherBodies) {
                JSONArray works = otherBody.getJSONArray("works");
                for (int i = 0; i < works.size(); i++) {
                    worksList = worksList != null ? worksList : new ArrayList<>();
                    worksList.add(works.getJSONObject(i));
                }
            }
        }
        log.debug("获取 {} 个作品 耗时 {} 毫秒", total, System.currentTimeMillis() - start);
        return worksList;
    }

    /**
     * 批量修改tag名称
     *
     * @param oldName 原tag名
     * @param newName 新tag名
     * @param tt      tt
     */
    public static void setTag(String cookie, String tt, String oldName, String newName) {
        log.info("修改Tag {} -> {}", oldName, newName);
        create(URL_TAG_SET)
                .addFormData("mode", "mod")
                .addFormData("tag", oldName)
                .addFormData("new_tag", newName)
                .addFormData("tt", tt)
                .setMaxTimes(1)
                .setTimeout(3000)
                .setCookie(cookie)
                .sendPost();
    }

    /**
     * 搜索作品
     *
     * @param keyword     关键字
     * @param p           页数(每页固定上限60个)
     * @param cookie      cookie(可选 不提供时不能搜索R-18作品)
     * @param searchTitle true = 搜索标题 false =搜 索tag
     * @param mode        模式 可取值： all safe r18
     * @return 搜索结果
     */
    public static JSONArray search(String keyword, Integer p, String cookie, boolean searchTitle, String mode) {
        List<String> availableMode = new ArrayList<>(Arrays.asList("all", "safe", "r18"));
        if (mode == null || !availableMode.contains(mode)) {
            mode = "all";
        }

        p = p == null || p < 0 ? 1 : p;
        log.info("搜索{} 关键字: {}", searchTitle ? "标题" : "标签", keyword);
        JSONObject body = create(URL_ILLUST_SEARCH)
                .addParamMap("keyword", ReqUtil.encode(keyword, "utf-8"))
                .addParamMap("s_mode", searchTitle ? "s_tc" : "s_tag")
                .addParamMap("mode", mode)
                .addParamMap("p", String.valueOf(p))
                .setCookie(cookie)
                .sendGet()
                .getBody("搜索");
        if (body != null) {
            JSONObject illustManga = body.getJSONObject("illustManga");
            Integer total = illustManga.getInteger("total");
            JSONArray data = illustManga.getJSONArray("data");
            log.info("搜索{} 关键字: {} 获得结果 {} 个 总计结果 {} 个", searchTitle ? "标题" : "标签", keyword, data.size(), total);
            return data;
        }

        return null;
    }

    /**
     * 添加收藏 添加tag(可选)
     *
     * @param pid    pid
     * @param cookie cookie
     * @param tt     tt
     * @param tags   tags
     * @return 如果成功返回true 失败返回其pid
     */
    public static Object bmk(String pid, String cookie, String tt, String tags) {
        tags = tags == null ? "" : tags.replace(",", " ");
        log.info("添加收藏 {} tags: {}", pid, tags);
        JSONObject body = create(URL_BOOKMARKS_ADD).setCookie(cookie)
                .addFormData("mode", "save_illust_bookmark")
                .addFormData("illust_id", pid)
                .addFormData("restrict", "0")
                .addFormData("comment", "")
                .addFormData("tags", tags)
                .addFormData("tt", tt)
                .sendPost().getBody("请求收藏 " + pid);
        return body != null ? true : pid;

    }

    /**
     * 批量添加收藏
     *
     * @param pidAndTags pid及其对应的tag
     * @param cookie     cookie
     * @param tt         tt
     * @param executor   线程池
     * @param progress   进度对象
     * @return 如果有失败任务 返回其pid
     */
    public static List<Object> bmk(Map<String, String> pidAndTags, String cookie, String tt, ThreadPoolTaskExecutor executor, Progress progress) {
        long start = System.currentTimeMillis();
        Set<String> pidSet = pidAndTags.keySet();
        log.info("添加收藏任务 {}个", pidAndTags.size());

        List<Callable<Object>> tasks = new ArrayList<>();
        for (String pid : pidSet) {
            tasks.add(() -> {
                Object bmk = PixivPost.bmk(pid, cookie, tt, pidAndTags.get(pid));
                if (progress != null) {
                    progress.add(1);
                }
                return bmk;
            });
        }
        //执行结果
        List<Object> bmk = executeTasks(tasks, 5, executor, "bmk", 2);

        bmk.removeIf(o -> o instanceof Boolean);

        log.info("批量收藏 {} 个作品 失败 {} 个 耗时 {} 毫秒", pidAndTags.size(), bmk.size(), System.currentTimeMillis() - start);

        return bmk;
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

    private static PixivPost create(String rawUrl) {
        return new PixivPost(rawUrl);
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
        if (paramMap == null) {
            return;
        }
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
        paramMap = paramMap != null ? paramMap : new HashMap<>(5);
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
        formData = formData != null ? formData : new HashMap<>(5);
        if (v != null && !"".equals(v)) {
            log.debug("formData {} -> {}", k, v);
        }
        formData.put(k, v);
        return this;
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
     * @param executorName   自带线程池名称
     * @param defaultSize    自带线程池size
     * @param <T>            返回类型
     * @return 结果列表
     */
    public static <T> List<T> executeTasks(Collection<Callable<T>> tasks, Integer timeoutSeconds, ThreadPoolTaskExecutor executor, String executorName, Integer defaultSize) {
        boolean b = executor == null;
        if (b) {
            log.info("使用自创线程池执行任务");
            executor = getExecutor(defaultSize, executorName);
        }

        List<Future<T>> futures = new ArrayList<>();
        List<T> resultList = null;
        //把任务提交到线程池 并保存future对象
        for (Callable<T> task : tasks) {
            Future<T> future = executor.submit(task);
            futures.add(future);
        }

        for (Future<T> future : futures) {
            try {
                //获取future对象的执行结果（阻塞）
                T result = future.get(timeoutSeconds, TimeUnit.SECONDS);
                resultList = resultList == null ? new ArrayList<>() : resultList;
                //把执行结果放入List
                resultList.add(result);

            } catch (ExecutionException | TimeoutException | InterruptedException e) {
                // 执行失败或超时时取消任务
                future.cancel(true);
            }
        }
        //任务执行完毕 且 已取消未完成任务

        //使用自身创建的线程池时关闭线程池
        if (b) {
            executor.shutdown();
        }
        return resultList;
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


        log.info("执行完毕");
    }
}


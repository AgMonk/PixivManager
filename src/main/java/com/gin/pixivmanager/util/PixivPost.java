package com.gin.pixivmanager.util;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.*;
import java.util.concurrent.Callable;

import static com.gin.pixivmanager.util.JsonUtil.printJson;
import static com.gin.pixivmanager.util.Request.timeCost;

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
    final static String URL_ILLUST_DETAIL = "https://www.pixiv.net/ajax/illust/";
    /**
     * 获取收藏作品接口 需要cookie
     */
    final static String URL_BOOKMARKS_GET = "https://www.pixiv.net/ajax/user/{uid}/illusts/bookmarks";
    /**
     * 给单个作品添加tag接口 需要cookie
     */
    final static String URL_TAG_ADD = "https://www.pixiv.net/bookmark_add.php?id=";
    /**
     * 批量修改tag名称接口 需要cookie
     */
    final static String URL_TAG_SET = "https://www.pixiv.net/bookmark_tag_setting.php";
    /**
     * 搜索作品接口(cookie可选)
     */
    final static String URL_ILLUST_SEARCH = "https://www.pixiv.net/ajax/search/artworks/";
    /**
     * 添加收藏接口 需要cookie
     */
    final static String URL_BOOKMARKS_ADD = "https://www.pixiv.net/rpc/index.php";
    /**
     * 搜索用户作品接口
     */
    final static String URL_USER_ILLUST = "https://www.pixiv.net/ajax/user/{uid}/profile/all";

    /*—————————— 公开方法 ——————————*/

    /**
     * 查询作品详情
     *
     * @param pid    pid
     * @param cookie cookie
     * @return 如无错误 返回body对象 否则为null
     */
    public static JSONObject detail(String pid, String cookie) {

        long start = System.currentTimeMillis();
        log.debug("请求作品详情{} {}", cookie == null ? "" : "(cookie)", pid);

        Request request = Request.create(URL_ILLUST_DETAIL + pid)
                .setCookie(cookie).get();
        String result = request.getResult();
        JSONObject resultJson = result == null ? null : JSONObject.parseObject(result);
        JSONObject body = resultJson == null ? null : resultJson.getJSONObject("body");


        long end = System.currentTimeMillis();
        log.debug("获得作品详情{} {} 耗时 {} 毫秒", body != null ? "成功" : "失败", pid, timeCost(start, end));
        return body;
    }

    /**
     * 批量查询详情
     *
     * @param pidSet      pid集合
     * @param cookie      cookie
     * @param executor    线程池
     * @param progressMap 进度对象
     * @return 详情列表
     */
    public static List<JSONObject> detail(Set<String> pidSet, String cookie, ThreadPoolTaskExecutor executor, Map<String, Integer> progressMap) {
        List<Callable<JSONObject>> tasks = new ArrayList<>();
        long start = System.currentTimeMillis();
        log.debug("请求作品详情 {} 个", pidSet.size());
        for (String pid : pidSet) {
            tasks.add(() -> {
                JSONObject detail = detail(pid, cookie);
                addProgress(progressMap);
                return detail;
            });
        }
        List<JSONObject> detail = TasksUtil.executeTasks(tasks, 60, executor, "detail", 2);
        long end = System.currentTimeMillis();
        log.debug("获得作品详情 {} 个 耗时 {}", detail.size(), timeCost(start, end));
        return detail;
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

        Request.create(URL_TAG_ADD + pid)
//                .setContentType(Request.CONTENT_TYPE_X_WWW_FORM_URLENCODED)
                .setMaxTimes(1)
                .setTimeOutSecond(3)
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
                .post();

    }

    /**
     * 批量添加tag
     *
     * @param pidAndTags  pid  和tags
     * @param cookie      cookie
     * @param executor    线程池
     * @param progressMap 进度
     */
    public static void addTags(Map<String, String> pidAndTags, String cookie, String tt, ThreadPoolTaskExecutor executor, Map<String, Integer> progressMap) {
        List<Callable<Void>> tasks = new ArrayList<>();
        long start = System.currentTimeMillis();
        log.debug("添加Tag {} 个", pidAndTags.size());
        for (Map.Entry<String, String> entry : pidAndTags.entrySet()) {
            String pid = entry.getKey();
            String tags = entry.getValue();
            tasks.add(() -> {
                addTags(pid, cookie, tt, tags);
                addProgress(progressMap);
                return null;
            });
        }
        TasksUtil.executeTasks(tasks, 60, executor, "addTags", 2);
        completeProgress(progressMap);
        long end = System.currentTimeMillis();
        log.debug("添加Tag {} 个 耗时 {}", pidAndTags.size(), timeCost(start, end));
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

        String result = Request.create(URL_BOOKMARKS_GET.replace("{uid}", uid))
                .setCookie(cookie)
                .addParam("limit", limit)
                .addParam("offset", offset)
                .addParam("tag", tag)
                .addParam("lang", "zh")
                .addParam("rest", "show")
                .get().getResult();

        JSONObject body = getBody(result);

        long end = System.currentTimeMillis();

        if (body != null) {
            log.debug("获得收藏 UID={} 标签={} 第 {} 页 耗时 {} 毫秒", uid, tag, offset / limit + 1, timeCost(start, end));
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
    public static List<JSONObject> getBookmarks(String uid, String cookie, String tag, Integer page, ThreadPoolTaskExecutor executor, Map<String, Integer> progressMap) {
        long start = System.currentTimeMillis();
        page = (page == null || page < 1) ? 1 : page;
        int offset = 0;
        int limit = 10;
        //请求到的数量
        int total = 0;
        List<JSONObject> worksList = null;
        JSONObject body = getBookmarks(uid, cookie, tag, limit, offset);

        addProgress(progressMap);

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

                    addProgress(progressMap);

                    return bookmarks;
                });
                offset += limit;
            }
            List<JSONObject> otherBodies = TasksUtil.executeTasks(tasks, 60, executor, "bookmark", 2);
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

        Request.create(URL_TAG_SET).addFormData("mode", "mod")
                .addFormData("tag", oldName)
                .addFormData("new_tag", newName)
                .addFormData("tt", tt)
                .setMaxTimes(1)
                .setTimeOutSecond(3)
                .setCookie(cookie).post();

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
    public static List<JSONObject> search(String keyword, Integer p, String cookie, boolean searchTitle, String mode) {
        List<String> availableMode = new ArrayList<>(Arrays.asList("all", "safe", "r18"));
        if (mode == null || !availableMode.contains(mode)) {
            mode = "all";
        }

        p = p == null || p < 0 ? 1 : p;
        log.info("搜索{} 关键字: {}", searchTitle ? "标题" : "标签", keyword);

        String result = Request.create(URL_ILLUST_SEARCH + Request.encode(keyword, null))
                .setCookie(cookie)
                .addParam("s_mode", searchTitle ? "s_tc" : "s_tag")
                .addParam("mode", mode)
                .addParam("p", p)
                .get()
                .getResult();
        JSONObject body = getBody(result);

        if (body != null) {
            JSONObject illustManga = body.getJSONObject("illustManga");
            Integer total = illustManga.getInteger("total");
            JSONArray data = illustManga.getJSONArray("data");
            log.info("搜索{} 关键字: {} 获得结果 {} 个 总计结果 {} 个 共计 {} 页", searchTitle ? "标题" : "标签", keyword, data.size(), total, total / 60 + 1);

            List<JSONObject> resultList = new ArrayList<>();
            for (int i = 0; i < data.size(); i++) {
                resultList.add(data.getJSONObject(i));
            }
            return resultList;
        }

        return null;
    }

    /**
     * 批量搜索
     *
     * @param keywordSet  关键字
     * @param start       页数
     * @param cookie      cookie(可选 不提供时不能搜索R-18作品)
     * @param searchTitle true = 搜索标题 false =搜 索tag
     * @param mode        模式 可取值： all safe r18
     * @param executor    线程池
     * @param progressMap 进度
     * @return 搜索结果
     */
    public static List<JSONObject> search(Set<String> keywordSet, Integer start, Integer end, String cookie, boolean searchTitle
            , String mode, ThreadPoolTaskExecutor executor, Map<String, Integer> progressMap) {
        List<Callable<List<JSONObject>>> tasks = new ArrayList<>();
        for (String keyword : keywordSet) {
            for (int i = start; i <= end; i++) {
                Integer finalI = i;
                tasks.add(() -> {
                    List<JSONObject> search = search(keyword, finalI, cookie, searchTitle, mode);
                    addProgress(progressMap);
                    return search;
                });
            }
        }
        List<List<JSONObject>> search = TasksUtil.executeTasks(tasks, 60, executor, "search", 3);
        List<JSONObject> result = new ArrayList<>();
        for (List<JSONObject> list : search) {
            result.addAll(list);
        }
        return result;
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

        String result = Request.create(URL_BOOKMARKS_ADD)
                .setCookie(cookie)
                .addFormData("mode", "save_illust_bookmark")
                .addFormData("illust_id", pid)
                .addFormData("restrict", "0")
                .addFormData("comment", "")
                .addFormData("tags", tags)
                .addFormData("tt", tt)
                .post()
                .getResult();
        JSONObject body = getBody(result);


        return body != null ? true : pid;

    }

    /**
     * 批量添加收藏
     *
     * @param pidAndTags  pid及其对应的tag
     * @param cookie      cookie
     * @param tt          tt
     * @param executor    线程池
     * @param progressMap 进度对象
     * @return 如果有失败任务 返回其pid
     */
    public static List<Object> bmk(Map<String, String> pidAndTags, String cookie, String tt, ThreadPoolTaskExecutor executor, Map<String, Integer> progressMap) {
        long start = System.currentTimeMillis();
        Set<String> pidSet = pidAndTags.keySet();
        log.info("添加收藏任务 {}个", pidAndTags.size());

        List<Callable<Object>> tasks = new ArrayList<>();
        for (String pid : pidSet) {
            tasks.add(() -> {
                Object bmk = bmk(pid, cookie, tt, pidAndTags.get(pid));
                addProgress(progressMap);
                return bmk;
            });
        }
        //执行结果
        List<Object> bmk = TasksUtil.executeTasks(tasks, 5, executor, "bmk", 2);

        bmk.removeIf(o -> o instanceof Boolean);

        log.info("批量收藏 {} 个作品 失败 {} 个 耗时 {} 毫秒", pidAndTags.size(), bmk.size(), System.currentTimeMillis() - start);

        return bmk;
    }



    /*—————————— 基础方法 ————————————*/


    /**
     * 判断请求是否成功 如果成功返回body json对象
     *
     * @param result 自定义错误消息内容
     */
    private static JSONObject getBody(String result) {
        if (result != null) {
            JSONObject json = JSONObject.parseObject(result);
            String error = "error";
            if (json.getBoolean(error)) {
                //出错
                throw new RuntimeException(json.getString("message"));
            } else {
                return json.getJSONObject("body");
            }
        }
        return null;
    }

    public static void main(String[] args) {
        JSONObject detail = detail("84525393", null);
        printJson(detail);
        log.info("执行完毕");
    }


    /**
     * 进度+1
     *
     * @param progressMap 进度
     */
    private static void addProgress(Map<String, Integer> progressMap) {
        progressMap.put("count", progressMap.get("count") + 1);
    }

    /**
     * 完成进度
     *
     * @param progressMap 进度
     */
    private static void completeProgress(Map<String, Integer> progressMap) {
        progressMap.put("count", progressMap.get("size"));
    }

}


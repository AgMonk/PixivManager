package com.gin.pixivmanager.service;

import com.alibaba.fastjson.JSONObject;
import com.gin.pixivmanager.entity.DownloadFile;
import com.gin.pixivmanager.entity.Illustration;
import com.gin.pixivmanager.entity.Tag;
import com.gin.pixivmanager.util.PixivPost;
import com.gin.pixivmanager.util.Request;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author bx002
 */
@Slf4j
@Service
public class PixivRequestServImpl implements PixivRequestServ {
    final ThreadPoolTaskExecutor downloadExecutor, requestExecutor, scanExecutor, downloadMainExecutor, slowSearchExecutor;
    final DataManager dataManager;
    final UserInfo userInfo;
    /**
     * 上次更新时间超过该时间的进行联网查询
     */
    final static long RANGE_OF_LAST_UPDATE = 30L * 24 * 60 * 60 * 1000;

    public PixivRequestServImpl(ThreadPoolTaskExecutor downloadExecutor,
                                ThreadPoolTaskExecutor requestExecutor,
                                ThreadPoolTaskExecutor scanExecutor,
                                ThreadPoolTaskExecutor downloadMainExecutor,
                                ThreadPoolTaskExecutor slowSearchExecutor, DataManager dataManager,
                                UserInfo userInfo) {
        this.downloadExecutor = downloadExecutor;
        this.requestExecutor = requestExecutor;
        this.scanExecutor = scanExecutor;
        this.downloadMainExecutor = downloadMainExecutor;
        this.slowSearchExecutor = slowSearchExecutor;
        this.dataManager = dataManager;
        this.userInfo = userInfo;


    }


    /**
     * 请求一个列表中的pid详情 并添加到数据库
     *
     * @param idSet        pid
     * @param idBookmarked 是否是已收藏作品
     * @return 作品详情
     */
    @Override
    public Set<Illustration> getIllustrationDetail(Set<String> idSet, boolean idBookmarked) {

        Set<Illustration> detailSet = dataManager.getIllustrations(idSet);

        //过滤出缓存和数据库中均不存在的、或数据库中过旧的详情数据 向pixiv请求 如有数据则加入
        Set<String> detailId = detailSet.stream().map(Illustration::getId).collect(Collectors.toSet());

        idSet = idSet.stream().filter(s -> !detailId.contains(s)).collect(Collectors.toSet());
        detailSet.stream()
                .filter(ill -> ill.getLastUpdate() == null
                        || ill.getLastUpdate() < System.currentTimeMillis() - RANGE_OF_LAST_UPDATE
                        || ill.getBookmarkData() == 0
                )
                .map(Illustration::getId).forEach(idSet::add);


        if (idSet.size() > 0) {
            List<Illustration> detailFromPixiv = getIllustrationFromPixiv(idSet, requestExecutor);
            if (detailFromPixiv.size() > 0) {
                if (idBookmarked) {
                    log.info("设置为已收藏作品");
                    detailFromPixiv.forEach(ill -> ill.setBookmarkData(1));
                }

                detailSet.addAll(detailFromPixiv);
                dataManager.addIllustrations(detailFromPixiv);
                dataManager.addTags(detailFromPixiv);
                log.info("Pixiv请求到 {} 条数据", detailFromPixiv.size());
            }
        }


        return detailSet;
    }

    /**
     * 获取收藏的作品id
     *
     * @param tag  需要有的tag
     * @param page 最大获取数量
     * @return 收藏的作品id
     */
    @Override
    public Set<String> getBookmarks(String tag, Integer page) {
        Set<String> idSet = new HashSet<>();
        Map<String, Integer> progressMap = Request.createProgressMap(page);
        dataManager.addProgressMain("获取收藏作品ID", progressMap);
        List<JSONObject> bookmarks = PixivPost.getBookmarks(userInfo.getUid(), userInfo.getCookie(), tag, page, scanExecutor, progressMap);
        if (bookmarks != null) {
            for (JSONObject bookmark : bookmarks) {
                idSet.add(bookmark.getString("id"));
            }
        }
        return idSet;
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

        String translation = tag.getTranslation().replace(" ", "");

        if (translation.contains(leftBrackets)) {
            translation = translation.substring(0, translation.indexOf(leftBrackets));
        }
        if (translation.contains(leftChineseBrackets)) {
            translation = translation.substring(0, translation.indexOf(leftChineseBrackets));
        }
        tag.setTranslation(translation);

        requestExecutor.execute(() -> PixivPost.setTag(userInfo.getCookie(), userInfo.getTt(), tag.getName(), tag.getTranslation()));


    }

    @Override
    public void addTags(Set<Illustration> detail) {
        Map<String, Integer> progressMap = Request.createProgressMap(detail.size());
        dataManager.addProgressMain("添加Tag", progressMap);
        Map<String, String> pidAndTags = new HashMap<>();
        detail.forEach(ill -> pidAndTags.put(ill.getId(), ill.createSimpleTags()));
        PixivPost.addTags(pidAndTags, userInfo.getCookie(), userInfo.getTt(), null, progressMap);
    }


    @Override
    public Set<String> archive(String[] name) {
        if (name == null || name.length == 0) {
            return null;
        }
        Set<String> idSet = new HashSet<>();
        for (String s : name) {
            String pid = getPidFromFileName(s);
            idSet.add(pid);
        }
        log.info("归档 {} 个文件 来自 {} 个作品", name.length, idSet.size());
        //获得id的详情信息 使用cookie查询
        Set<Illustration> detail = getIllustrationDetail(idSet, true);
        idSet = new HashSet<>();
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
                    idSet.add(key);
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
                    log.info("目标文件已存在 且大小不同 请自行确认保留 {}", dest.getPath());
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
                    idSet.add(key);

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
        log.info("归档 {} 个文件 完成", idSet.size());

        //添加收藏
        Map<String, String> pidAndTags = new HashMap<>();
        detail.forEach(ill -> {
            if (ill.getBookmarkData() == 0) {
                pidAndTags.put(ill.getId(), ill.createSimpleTags());
            }
        });
        if (pidAndTags.size() > 0) {
            Map<String, Integer> progressMap = Request.createProgressMap(pidAndTags.size());
            dataManager.addProgressMain("收藏作品", progressMap);
            PixivPost.bmk(pidAndTags, userInfo.getCookie(), userInfo.getTt(), null, progressMap);
        }

        return idSet;
    }

    @Override
    public void downloadIllust(Set<Illustration> details, String rootPath) {
        Set<DownloadFile> downloadFileList = new HashSet<>();
        for (Illustration ill : details) {
            for (String url : ill.getUrls()) {
                downloadFileList.add(new DownloadFile(url, rootPath + url.substring(url.lastIndexOf("/"))));
            }
        }
        dataManager.addDownload(downloadFileList);
    }


    @Override
    public Set<Illustration> search(Set<String> keywordSet, Integer start, Integer end, boolean all, ThreadPoolTaskExecutor executor) {
        Map<String, Integer> progressMap = Request.createProgressMap(keywordSet.size() * (end - start + 1));
        dataManager.addProgressMain("搜索任务", progressMap);

        List<JSONObject> searchResult = PixivPost.search(keywordSet, start, end, userInfo.getCookie(), false, "all", executor, progressMap);

        Set<Illustration> set = new HashSet<>();
        for (JSONObject jsonobj : searchResult) {
            set.add(new Illustration(jsonobj));
        }
        //
        if (!all) {
            set.removeIf(ill -> ill.getBookmarkData() == 1 || dataManager.getIllustrationMap().containsKey(ill.getId()));
        }

        log.info("搜索得到 {} 个作品 {}", set.size(), !all ? "已过滤掉已收藏的和已入库的作品" : "");
        return set;
    }

    @Override
    public void slowSearch(String keyword) {
        Set<String> keywordSet = new HashSet<>();
        keywordSet.add(keyword);
        int page = 1;
        int count = 0;
        Set<Illustration> search;
        do {
            search = search(keywordSet, page, page + 1, false, slowSearchExecutor);
            if (search.size() > 0) {
                Set<String> set = new HashSet<>();
                search.forEach(ill -> set.add(ill.getId()));
                dataManager.addSlowSearchPid(set);
                count += search.size();
            }
            page += 2;
        } while (search.size() > 0);
        log.info("搜索完毕 添加 {} 个 慢搜索Pid", count);
    }


    @Override
    public void slowDetail() {
        Set<String> searchPidSet = dataManager.getSlowSearchPidSet();
        if (searchPidSet.size() == 0) {
            return;
        }
        log.info("慢详情 剩余 {} 个", searchPidSet.size());
        Set<String> set = new HashSet<>();
        int count = 0;
        for (String pid : searchPidSet) {
            set.add(pid);
            count++;
            if (count == 10) {
                break;
            }
        }

        Set<Illustration> detail = getIllustrationDetail(set, false);

        Set<Illustration> detailSet;
        detailSet = detail.stream().filter(ill -> ill.getBookmarkCount() >= 1000).collect(Collectors.toSet());
        downloadIllust(detailSet, userInfo.getRootPath() + "/slowSearch/bmk1000_");
        detailSet = detail.stream().filter(ill -> ill.getBookmarkCount() >= 500 && ill.getBookmarkCount() < 1000).collect(Collectors.toSet());
        downloadIllust(detailSet, userInfo.getRootPath() + "/slowSearch/bmk500_");
        detailSet = detail.stream().filter(ill -> ill.getBookmarkCount() >= 200 && ill.getBookmarkCount() < 500).collect(Collectors.toSet());
        downloadIllust(detailSet, userInfo.getRootPath() + "/slowSearch/bmk200_");
        dataManager.removeSlowSearchPid(set);
    }

    @Override
    public Integer downloadSearch(Map<String, Integer> keywordAndPage, boolean all) {
        return null;
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
     * 使用多线程请求多个作品详情
     *
     * @param pidSet   请求详情的id列表
     * @param executor 线程池
     * @return 请求到的详情
     */
    private List<Illustration> getIllustrationFromPixiv(Set<String> pidSet, ThreadPoolTaskExecutor executor) {
        //添加进度
        Map<String, Integer> progressMap = Request.createProgressMap(pidSet.size());
        dataManager.addProgressMain("详情任务", progressMap);

        List<JSONObject> detail = PixivPost.detail(pidSet, null, executor, progressMap);

        List<Illustration> illusts = new ArrayList<>();
        if (detail == null) {
            return illusts;
        }
        for (JSONObject json : detail) {
            if (json != null) {
                illusts.add(new Illustration(json));
            }
        }
        return illusts;
    }
}


package com.gin.pixivmanager.controller;

import com.gin.pixivmanager.entity.Illustration;
import com.gin.pixivmanager.entity.Tag;
import com.gin.pixivmanager.service.DataManager;
import com.gin.pixivmanager.service.PixivRequestServ;
import com.gin.pixivmanager.service.UserInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

/**
 * pixiv接口
 *
 * @author bx002
 */
@Slf4j
@RestController
@RequestMapping("pixiv")
public class PixivController {
    final DataManager dataManager;
    final PixivRequestServ pixivRequestServ;
    final UserInfo userInfo;
    final ThreadPoolTaskExecutor scanExecutor;

    final String untaggedLocker = "";
    Set<String> keywordSet;

    public PixivController(DataManager dataManager, PixivRequestServ pixivRequestServ, UserInfo userInfo, ThreadPoolTaskExecutor scanExecutor) {
        this.dataManager = dataManager;
        this.pixivRequestServ = pixivRequestServ;
        this.userInfo = userInfo;
        this.scanExecutor = scanExecutor;
    }

    /**
     * 下载未分类作品
     */
    @RequestMapping("downloadUntagged")
    @Scheduled(cron = "0 0/10 * * * *")
    @Async(value = "controllerExecutor")
    public void downloadUntagged() {
        log.info("未分类任务加入队列");

        synchronized (untaggedLocker) {
            downloadBookmark("未分類", 1);
        }
    }

    /**
     * 下载收藏中指定tag的作品
     *
     * @return 文件列表
     */
    @RequestMapping("downloadBookmark")
    public void downloadBookmark(String tag, Integer page) {
        tag = tag != null ? tag : "未分類";

        Set<String> idSet = pixivRequestServ.getBookmarks(tag, page);
        if (idSet.size() > 0) {
            List<Illustration> detail = pixivRequestServ.getIllustrationDetail(idSet, true);
            pixivRequestServ.downloadIllust(detail, userInfo.getRootPath() + "/" + tag);
            pixivRequestServ.addTags(detail);
        }
//        dataManager.download();
    }

    @RequestMapping("addTranslation")
    public Integer addTranslation(Tag tag) {
        pixivRequestServ.setTag(tag);
        return dataManager.addTranslation(tag);
    }

    /**
     * 文件归档(重命名)
     *
     * @param name 文件名前缀
     * @return 已归档的文件名
     */
    @RequestMapping("archive")
    public Set<String> archive(String... name) {
        return pixivRequestServ.archive(name);
    }


    /**
     * 从旧到新自动归档
     */
    //    @Scheduled(cron = "0 5/10 * * * *")
    @RequestMapping("archiveOld")
    public void autoArchive() {
        List<Map<String, String>> filesPath = dataManager.getFilesPath();
        filesPath = filesPath.subList(Math.max(filesPath.size() - 50, 0), filesPath.size());
        List<String> nameList = new ArrayList<>();
        filesPath.forEach(map -> nameList.add(map.get("name")));

        int size = nameList.size();
        String[] name = new String[size];
        log.info("自动归档 {} 个作品", size);
        if (size == 0) {
            return;
        }
        nameList.toArray(name);
        pixivRequestServ.archive(name);
    }

    @RequestMapping("test")
    public Object test() {
        Set<String> pidSet = new HashSet<>();
        pidSet.add("84532363");
        pidSet.add("84532309");
        pidSet.add("84529965");


        log.info("测试接口运行完毕");
        return null;
    }

    /**
     * 定时从关键字中搜索一个并下载
     */
    @Scheduled(cron = "0 5/10 * * * *")
    @RequestMapping("autoSearch")
    public void autoDownloadSearch() {
        Map<String, Integer> keywordAndPage = new HashMap<>();
        if (keywordSet == null || keywordSet.size() == 0) {
            keywordSet = userInfo.getKeywordSet();
        }

        int i = 0;
        Set<String> set = new HashSet<>();
        Iterator<String> iterator = keywordSet.iterator();
        while (iterator.hasNext() && i < 2) {
            set.add(iterator.next());
            iterator.remove();
            i++;
        }

        Set<Illustration> search = pixivRequestServ.search(set, 1, false);
        if (search.size() == 0) {
            return;
        }
        HashSet<String> idSet = new HashSet<>();
        for (Illustration ill : search) {
            idSet.add(ill.getId());
        }
        List<Illustration> detail = pixivRequestServ.getIllustrationDetail(idSet, false);

        pixivRequestServ.downloadIllust(detail, userInfo.getRootPath() + "/search");
    }
}

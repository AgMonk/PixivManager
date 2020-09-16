package com.gin.pixivmanager.controller;

import com.gin.pixivmanager.entity.Illustration;
import com.gin.pixivmanager.entity.Tag;
import com.gin.pixivmanager.service.DataManager;
import com.gin.pixivmanager.service.PixivRequestServ;
import com.gin.pixivmanager.service.UserInfo;
import com.gin.pixivmanager.util.PixivPost;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    public List<File> downloadBookmark(String tag, Integer page) {
        tag = tag != null ? tag : "未分類";

        Set<String> idSet = pixivRequestServ.getBookmarks(tag, page);
        if (idSet.size() == 0) {
            return null;
        }
        List<Illustration> detail = pixivRequestServ.getIllustrationDetail(idSet);

        return pixivRequestServ.downloadIllustAndAddTags(detail, userInfo.getRootPath() + "/" + tag);
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
        String keyword = "(春田 or スプリングフィールド) -創一 -おっさんずラブ";
        PixivPost.search(userInfo.getCookie(), keyword, 1, false, "all");
        

        log.info("测试完毕");
        return null;
    }
}

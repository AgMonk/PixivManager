package com.gin.pixivmanager.controller;

import com.gin.pixivmanager.entity.Illustration;
import com.gin.pixivmanager.entity.Tag;
import com.gin.pixivmanager.service.DataManager;
import com.gin.pixivmanager.service.PixivRequestServ;
import com.gin.pixivmanager.service.UserInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.util.List;
import java.util.concurrent.Executor;

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
    final Executor controllerExecutor;

    final String untaggedLocker = "";

    public PixivController(DataManager dataManager, PixivRequestServ pixivRequestServ, UserInfo userInfo, Executor controllerExecutor) {
        this.dataManager = dataManager;
        this.pixivRequestServ = pixivRequestServ;
        this.userInfo = userInfo;
        this.controllerExecutor = controllerExecutor;
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
            List<File> list = downloadBookmark("未分類", 10);
        }
    }

    /**
     * 下载收藏中指定tag的作品
     *
     * @return 文件列表
     */
    @RequestMapping("downloadBookmark")
    public List<File> downloadBookmark(String tag, Integer max) {
        tag = tag != null ? tag : "未分類";

        List<String> idList = pixivRequestServ.getBookmarks(tag, max);
        if (idList.size() == 0) {
            return null;
        }
        List<Illustration> detail = pixivRequestServ.getIllustrationDetail(idList);
        List<File> download = pixivRequestServ.downloadIllustAndAddTags(detail, userInfo.getRootPath() + "/" + tag);

        return download;
    }

    @RequestMapping("addTranslation")
    public Integer addTranslation(Tag tag) {
        pixivRequestServ.setTag(tag);
        return dataManager.addTranslation(tag);
    }

    /**
     * 文件归档(重命名)
     *
     * @param name
     * @return
     */
    @RequestMapping("archive")
    public List<String> archive(String... name) {
        return pixivRequestServ.archive(name);
    }

    @RequestMapping("test")
    public Object test() {
        String[] array = new String[]{"84312013", "84312363"};
        List<File> files = pixivRequestServ.downloadIllustAndAddTags(array, userInfo.getRootPath() + "/未分類");
        System.err.println(files);
        return null;
    }
}

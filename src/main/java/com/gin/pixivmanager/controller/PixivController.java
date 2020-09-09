package com.gin.pixivmanager.controller;

import com.gin.pixivmanager.entity.Illustration;
import com.gin.pixivmanager.entity.Tag;
import com.gin.pixivmanager.service.DataManager;
import com.gin.pixivmanager.service.PixivRequestServ;
import com.gin.pixivmanager.service.UserInfo;
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
@RestController
@RequestMapping("pixiv")
public class PixivController {
    final DataManager dataManager;
    final PixivRequestServ pixivRequestServ;
    final UserInfo userInfo;
    final Executor controllerExecutor;

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
    @Async("controllerExecutor")
    public void downloadUntagged() {
        downloadBookmark("未分類", 10);
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
        List<Illustration> detail = pixivRequestServ.getIllustrationDetail(idList);
        List<File> download = pixivRequestServ.download(detail, userInfo.getRootPath() + "/" + tag);
        pixivRequestServ.addTags(detail);
        return download;
    }

    @RequestMapping("addTranslation")
    public Integer addTranslation(Tag tag) {
        pixivRequestServ.setTag(tag);
        return dataManager.addTranslation(tag);
    }


    @RequestMapping("test")
    public Object test() {

        return null;
    }
}

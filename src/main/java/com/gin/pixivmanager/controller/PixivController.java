package com.gin.pixivmanager.controller;

import com.gin.pixivmanager.entity.Illustration;
import com.gin.pixivmanager.entity.Tag;
import com.gin.pixivmanager.service.DataManager;
import com.gin.pixivmanager.service.PixivRequestServ;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.util.List;

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

    public PixivController(DataManager dataManager, PixivRequestServ pixivRequestServ) {
        this.dataManager = dataManager;
        this.pixivRequestServ = pixivRequestServ;
    }

    /**
     * 下载未分类作品
     *
     * @param tag
     * @return
     */
    @RequestMapping("downloadUntagged")
    public List<File> downloadUntagged(Tag tag) {
        List<String> untagged = pixivRequestServ.getBookmarks("未分類", 100);
        List<Illustration> detail = pixivRequestServ.getIllustrationDetail(untagged);
        List<File> download = pixivRequestServ.download(detail, "f:/");
        /* todo 请求pixiv添加tag*/
        return download;
    }

    @RequestMapping("addTranslation")
    public Integer addTranslation(Tag tag) {
        /*todo 请求pixiv修改tag*/
        return dataManager.addTranslation(tag);
    }


    @RequestMapping("test")
    public Object test() {

        return null;
    }
}

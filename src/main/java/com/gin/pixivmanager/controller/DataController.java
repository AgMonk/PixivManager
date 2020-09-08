package com.gin.pixivmanager.controller;

import com.gin.pixivmanager.entity.Illustration;
import com.gin.pixivmanager.entity.Tag;
import com.gin.pixivmanager.service.DataManager;
import com.gin.pixivmanager.service.PixivRequestServ;
import com.gin.pixivmanager.util.SpringContextUtil;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 数据控制器
 */
@RestController
@RequestMapping("data")
public class DataController {
    final DataManager dataManager;

    public DataController(DataManager dataManager) {
        this.dataManager = dataManager;
    }

    @RequestMapping("init")
    public void init() {
        dataManager.init();
    }

    @RequestMapping("getTags")
    public List<Tag> getTags(Integer page, Integer limit, String keyword, Integer all) {
        return dataManager.getTags(page, limit, keyword, all);
    }

    @RequestMapping("setTagTranslation")
    public Integer setTagTranslation(Tag tag) {
        return dataManager.setTagTranslation(tag);
    }

    @RequestMapping("progress")
    public Map<String, String> getDownloadingProgress() {
        return dataManager.getDownloading();
    }


    @RequestMapping("test")
    public List<Illustration> test() {
        List<String> list = new ArrayList<>();
        list.add("84232385");
        list.add("84234141");
        list.add("84228932");

        PixivRequestServ pixivRequestServ = SpringContextUtil.getBean(PixivRequestServ.class);
        return pixivRequestServ.getIllustrationDetail(list);
    }
}

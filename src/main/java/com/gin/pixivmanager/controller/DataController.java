package com.gin.pixivmanager.controller;

import com.gin.pixivmanager.entity.Tag;
import com.gin.pixivmanager.service.DataManager;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

    @RequestMapping("notTranslatedTags")
    public List<Tag> getNotTranslatedTags(Integer page, Integer limit) {
        return dataManager.getNotTranslatedTags(page, limit);
    }

    @RequestMapping("setTagTranslation")
    public Integer setTagTranslation(Tag tag) {
        return dataManager.setTagTranslation(tag);
    }
}

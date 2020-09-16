package com.gin.pixivmanager.controller;

import com.gin.pixivmanager.entity.Tag;
import com.gin.pixivmanager.service.DataManager;
import com.gin.pixivmanager.util.Progress;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 数据控制器
 *
 * @author bx002
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

    @RequestMapping("downloading")
    public Map<String, String> getDownloadingProgress() {
        return dataManager.getDownloading();
    }

    @RequestMapping("details")
    public Map<String, String> getDetails() {
        return dataManager.getDetails();
    }


    @RequestMapping("getFilesPath")
    public List<Map<String, String>> getFilesPath() {
        List<Map<String, String>> list = dataManager.getFilesPath();
        return list.subList(0, Math.min(list.size(), 50));
    }

    /**
     * 移动文件到归档文件夹，不改名
     */
    @RequestMapping("move")
    public void moveFile(String... name) {
        dataManager.moveFile(name);
    }

    @RequestMapping("delFile")
    public List<String> delFile(String... name) {
        List<String> names = new ArrayList<>();
        for (String s : name) {
            names.add(dataManager.delFile(s));
        }
        return names;
    }

    @RequestMapping("getProgress")
    public Map<String, List<Progress>> getProgress() {
        return dataManager.getProgress();
    }

    @RequestMapping("uploadTwitter")
    public void uploadTwitter(HttpServletResponse response, MultipartFile[] file, String title, String tags) {
        for (MultipartFile f : file) {
            dataManager.uploadTwitter(f, title, tags);
        }
        try {
            response.sendRedirect("/twitter.html");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @RequestMapping("test")
    public Object test() {
        return null;
    }
}

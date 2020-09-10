package com.gin.pixivmanager.controller;

import com.gin.pixivmanager.entity.Illustration;
import com.gin.pixivmanager.service.DataManager;
import com.gin.pixivmanager.service.NgaPostServ;
import com.gin.pixivmanager.service.UserInfo;
import com.gin.pixivmanager.util.NgaPost;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("nga")
public class NgaController {
    final NgaPostServ ngaPostServ;
    final DataManager dataManager;
    final UserInfo userInfo;

    public NgaController(NgaPostServ ngaPostServ, DataManager dataManager, UserInfo userInfo) {
        this.ngaPostServ = ngaPostServ;
        this.dataManager = dataManager;
        this.userInfo = userInfo;
    }

    @RequestMapping("repost")
    public String repost(String... ids) {
        String cookie = userInfo.getNgaCookie("左牵黄");
        String fid = userInfo.getNgaFid("测试版面");
        String tid = userInfo.getNgaTid("测试楼");
//        String fid = userInfo.getNgaFid("少女前线");
//        String tid = userInfo.getNgaTid("少前水楼");
        String action = NgaPost.ACTION_REPLY;
        NgaPost ngaPost = NgaPost.create(cookie, fid, tid, action);
        String wrap = ngaPost.getWrap();

        //准备附件
        Map<String, File> map = ngaPostServ.prepare4Files(ids);
        //上传附件
        Set<String> attachmentsName = ngaPost.uploadFiles(map);

        dataManager.getIllustrations(Arrays.asList(ids));

        StringBuilder sb = new StringBuilder();

        for (String id : ids) {
            StringBuilder attachments = new StringBuilder();
            for (String name : attachmentsName) {
                if (id.equals(name) || name.contains(id + "_")) {
                    attachments.append(ngaPost.getAttachmentCode(name)).append(wrap);
                }
            }
            Illustration ill = dataManager.getIllustrationMap().get(id);
            //作者链接
            String userUrl = "作者:" + NgaPost.getUrlCode(ill.getUserName(), ill.getUserUrl());
            String title = ill.getTitle();
            String collapseTitle;
            String description = ill.getDescription();
            String link = NgaPost.getUrlCode("ToPixiv", ill.getLink());
            String pid = id + " " + link;

            StringBuilder content = new StringBuilder();
            content.append(userUrl).append(" ");
            content.append(pid).append(wrap);
            if (title != null && !"".equals(title)) {
                content.append("标题:").append(title).append(wrap);
                collapseTitle = title;
            } else {
                collapseTitle = id;
            }
            if (description != null && !"".equals(description)) {
                content.append("描述:").append(description).append(wrap);
            }
            //附件
            content.append(attachments);

            String collapse = NgaPost.getCollapse(collapseTitle, content.toString());

            sb.append(collapse);
        }
        String quote = NgaPost.getQuote(sb.toString());
        ngaPost.addContent(quote);
        ngaPost.addTitle("Pixiv搬运bot酱");

        String send = ngaPost.send();
        log.info("发帖成功: {}", send);

        //删除临时文件
        for (File f : map.values()) {
            f.delete();
        }
        return send;
    }
}

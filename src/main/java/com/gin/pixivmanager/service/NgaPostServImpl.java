package com.gin.pixivmanager.service;

import com.gin.pixivmanager.util.NgaPost;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * @author bx002
 */
@Service
@Slf4j
public class NgaPostServImpl implements NgaPostServ {
    final UserInfo userInfo;

    public NgaPostServImpl(UserInfo userInfo) {
        this.userInfo = userInfo;

        test();
    }

    private void test() {
        String cookie = userInfo.getNgaCookie("左牵黄");
        String fid = userInfo.getNgaFid("测试版面");
        String tid = userInfo.getNgaTid("测试楼");
        String action = NgaPost.ACTION_REPLY;

        NgaPost ngaPost = NgaPost.create(cookie, fid, tid, action);
        File[] files = new File[]{};
        Map<String, File> map = new HashMap<>();
        map.put("84256106_p0", new File("F:/[pixiv]/未分類/84256106_p0.jpg"));
        map.put("84256700_p0", new File("F:/[pixiv]/未分類/84256700_p0.jpg"));
        ngaPost.uploadFiles(map);


        String img1 = ngaPost.getAttachmentCode("84256106_p0");
        String img2 = ngaPost.getAttachmentCode("84256700_p0");
        String collapse = NgaPost.getCollapse("img", img1 + img2);
        String quote = NgaPost.getQuote(collapse);

        ngaPost.addTitle("测试测试测试")
                .addContent(quote)
        ;


        String send = ngaPost.send();
        log.info("发帖成功: {}", send);
    }
}

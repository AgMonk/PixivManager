package com.gin.pixivmanager.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
//        String cookie = userInfo.getNgaCookie("左牵黄");
//        String fid = userInfo.getNgaFid("少女前线");
//        String tid = userInfo.getNgaTid("少前水楼");
//        String action = NgaPost.ACTION_REPLY;
//
//        File[] files = new File[]{};
//        Map<String, File> map = new HashMap<>();
//        map.put("84256106_p0", new File("F:/[pixiv]/未分類/84256106_p0.jpg"));
//        map.put("84256700_p0", new File("F:/[pixiv]/未分類/84256700_p0.jpg"));
//        NgaPost ngaPost = NgaPost.create(cookie, fid, tid, action);
//        ngaPost.uploadFiles(map);

    }
}

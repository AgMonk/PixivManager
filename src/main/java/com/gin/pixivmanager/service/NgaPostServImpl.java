package com.gin.pixivmanager.service;

import com.gin.pixivmanager.entity.Illustration;
import com.gin.pixivmanager.util.NgaPost;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @author bx002
 */
@Service
@Slf4j
public class NgaPostServImpl implements NgaPostServ {
    final UserInfo userInfo;
    final PixivRequestServ pixivRequestServ;
    final DataManager dataManager;
    final Executor serviceExecutor;

    public NgaPostServImpl(UserInfo userInfo, PixivRequestServ pixivRequestServ, DataManager dataManager, Executor serviceExecutor) {
        this.userInfo = userInfo;
        this.pixivRequestServ = pixivRequestServ;
        this.dataManager = dataManager;
        this.serviceExecutor = serviceExecutor;

    }

    /**
     * 为上传准备附件文件
     *
     * @return 附件列表
     */
    private Map<String, File> prepare4Files(String... ids) {
        String tempPath = userInfo.getRootPath() + "/temp/";
        List<String> lackList = new ArrayList<>();
        Map<String, File> filesMap = dataManager.getFilesMap();

        /**
         * 已准备好的列表
         */
        Map<String, File> map = new HashMap<>();

        for (String id : ids) {
            boolean b = true;

            //从现有文件中查找所需文件
            for (Map.Entry<String, File> entry : filesMap.entrySet()) {
                String key = entry.getKey();
                if (key.equals(id) || key.contains(id + "_")) {
                    b = false;
                    File source = entry.getValue();
                    String name = source.getName();
                    String suffix = name.substring(name.lastIndexOf('.'));
                    File destFile = new File(tempPath + key + suffix);

                    try {
                        log.info("发现所需文件 复制到 {}", destFile.getPath());
                        copyFile(source, destFile);
                        map.put(key, destFile);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }
            }
            //现有文件中没有
            if (b) {
                lackList.add(id);
            }

        }
        //有缺少的文件

        if (lackList.size() > 0) {
            log.info("缺少文件  {}个", lackList.size());
            List<Illustration> detail = pixivRequestServ.getIllustrationDetail(lackList);
            List<File> downloadList = pixivRequestServ.download(detail, tempPath);
            for (File file : downloadList) {
                String name = file.getName();
                name = name.substring(0, name.lastIndexOf('.'));
                map.put(name, file);
            }
        }
        return map;
    }

    @Override
    public String repost(String f,String t, String... ids) {
        String cookie = userInfo.getNgaCookie("左牵黄");
        String fid = userInfo.getNgaFid(f);
        String tid = userInfo.getNgaTid(t);
        String action = NgaPost.ACTION_REPLY;
        NgaPost ngaPost = NgaPost.create(cookie, fid, tid, action);
        String wrap = ngaPost.getWrap();

        //准备附件
        Map<String, File> map = prepare4Files(ids);
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
        for (File file : map.values()) {
            file.delete();
        }
        return send;
    }

    private void test() {
        String cookie = userInfo.getNgaCookie("左牵黄");
        String fid = userInfo.getNgaFid("测试版面");
        String tid = userInfo.getNgaTid("测试楼");
        String action = NgaPost.ACTION_REPLY;

        NgaPost ngaPost = NgaPost.create(cookie, fid, tid, action);


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


    private static void copyFile(File source, File dest) throws IOException {
        if (source.getPath().equals(dest.getPath())) {
            return;
        }

        FileChannel inputChannel = null;
        FileChannel outputChannel = null;
        try {
            inputChannel = new FileInputStream(source).getChannel();
            outputChannel = new FileOutputStream(dest).getChannel();
            outputChannel.transferFrom(inputChannel, 0, inputChannel.size());
        } finally {
            assert inputChannel != null;
            inputChannel.close();
            assert outputChannel != null;
            outputChannel.close();
        }
    }
}

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
import java.util.concurrent.CountDownLatch;
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
    private Map<String, File> prepare4Files(String... name) {
        String tempPath = userInfo.getRootPath() + "/temp/";
        /**
         * 缺少的文件名
         */
        List<String> lackList = new ArrayList<>();
        /**
         * 缺少的文件pid
         */
        List<String> lackPidList = new ArrayList<>();
        /**
         * 现成的文件
         */
        Map<String, File> filesMap = dataManager.getFilesMap(name);
        /**
         * 需要上传的文件
         */
        Map<String, File> uploadMap = new HashMap<>();

        //新线程 复制文件到temp目录
        CountDownLatch latch = new CountDownLatch(2);
        serviceExecutor.execute(() -> {
            for (Map.Entry<String, File> entry : filesMap.entrySet()) {
                //目标文件名
                String fileName = entry.getKey();
                //源文件
                File sourceFile = entry.getValue();
                String sFileName = sourceFile.getName();
                //后缀
                String suffix = sFileName.substring(sFileName.lastIndexOf('.'));
                //目标文件
                File destFile = new File(tempPath + fileName + suffix);

                try {
                    log.info("发现所需文件 复制到 {}", destFile.getPath());
                    copyFile(sourceFile, destFile);
                    uploadMap.put(fileName, destFile);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            latch.countDown();
        });

        //缺少的文件
        for (String s : name) {
            if (!filesMap.containsKey(s)) {
                String pid = s.substring(0, s.indexOf('_'));
                lackList.add(s);
                if (!lackPidList.contains(pid)) {
                    lackPidList.add(pid);
                }
            }
        }

        //有缺少文件
        serviceExecutor.execute(() -> {
            if (lackPidList.size() > 0) {
                //查询详情
                List<Illustration> detail = pixivRequestServ.getIllustrationDetail(lackPidList);
                //下载文件
                List<File> download = pixivRequestServ.download(detail, tempPath);
                for (File file : download) {
                    String fileName = file.getName();
                    String pidAndCount = fileName.substring(0, fileName.lastIndexOf('.'));
                    //发现需要文件
                    if (lackList.contains(pidAndCount)) {
                        //加入map
                        uploadMap.put(pidAndCount, file);
                    }
                }
            }
            latch.countDown();
        });

        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

//        /**
//         * 已准备好的列表
//         */
//        Map<String, File> map = new HashMap<>();
//
//        for (String id : name) {
//            boolean b = true;
//
//            //从现有文件中查找所需文件
//            for (Map.Entry<String, File> entry : filesMap.entrySet()) {
//                String key = entry.getKey();
//                if (key.equals(id) || key.contains(id + "_")) {
//                    b = false;
//                    File source = entry.getValue();
//                    String fileName = source.getName();
//                    String suffix = fileName.substring(fileName.lastIndexOf('.'));
//                    File destFile = new File(tempPath + key + suffix);
//
//                    try {
//                        log.info("发现所需文件 复制到 {}", destFile.getPath());
//                        copyFile(source, destFile);
//                        map.put(key, destFile);
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//
//                }
//            }
//            //现有文件中没有
//            if (b) {
//                lackList.add(id);
//            }
//
//        }
//        //有缺少的文件
//        if (lackList.size() > 0) {
//            log.info("缺少文件  {}个", lackList.size());
//            List<Illustration> detail = pixivRequestServ.getIllustrationDetail(lackList);
//            List<File> downloadList = pixivRequestServ.download(detail, tempPath);
//            for (File file : downloadList) {
//                String fileName = file.getName();
//                fileName = fileName.substring(0, fileName.lastIndexOf('.'));
//                map.put(fileName, file);
//            }
//        }
        return uploadMap;
    }

    @Override
    public String repost(String f, String t, String... name) {
        String cookie = userInfo.getNgaCookie("左牵黄");
        String fid = userInfo.getNgaFid(f);
        String tid = userInfo.getNgaTid(t);
        String action = NgaPost.ACTION_REPLY;
        NgaPost ngaPost = NgaPost.create(cookie, fid, tid, action);
        String wrap = ngaPost.getWrap();

        //准备附件
        Map<String, File> map = prepare4Files(name);
        //上传附件
        Set<String> attachmentsName = ngaPost.uploadFiles(map);

        dataManager.getIllustrations(Arrays.asList(name));
        /*todo 载入所有所涉id的详情数据*/

        StringBuilder sb = new StringBuilder();

        for (String id : name) {
            StringBuilder attachments = new StringBuilder();
            for (String aName : attachmentsName) {
                if (id.equals(aName) || aName.contains(id + "_")) {
                    attachments.append(ngaPost.getAttachmentCode(aName)).append(wrap);
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

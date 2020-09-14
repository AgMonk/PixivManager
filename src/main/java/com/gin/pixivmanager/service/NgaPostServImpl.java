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
    final static String UNDERSCORE = "_";

    final UserInfo userInfo;
    final PixivRequestServ pixivRequestServ;
    final DataManager dataManager;
    final Executor serviceExecutor;
    /**
     * 审核字符串
     */
    static Set<String> reviewKeyword = new HashSet<>();

    static {
        reviewKeyword.add("巨乳");
        reviewKeyword.add("屁股");
    }

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
        // 缺少的文件名
        List<String> lackList = new ArrayList<>();
        // 缺少的文件pid
        List<String> lackPidList = new ArrayList<>();
        // 现成的文件
        Map<String, File> filesMap = dataManager.getFilesMap(name);
        // 需要上传的文件
        Map<String, File> uploadMap = new HashMap<>(name.length);

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
                List<File> download = pixivRequestServ.downloadIllustAndAddTags(detail, tempPath);
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

        return uploadMap;
    }

    @Override
    public String repost(String f, String t, String... name) {
        String cookie = userInfo.getNgaCookie("左牵黄");
        String fid = userInfo.getNgaFid(f);
        String tid = userInfo.getNgaTid(t);
        String action = NgaPost.ACTION_REPLY;
        NgaPost ngaPost = NgaPost.create(cookie, fid, tid, action);

        //准备附件
        Map<String, File> map = prepare4Files(name);
        //上传附件
        ngaPost.uploadFiles(map);

        List<Illustration> illList = pixivRequestServ.getIllustrationDetail(Arrays.asList(name));
        log.info("查询得到作品详情 {}条", illList.size());
        StringBuilder sb = new StringBuilder();

        for (Illustration ill : illList) {
            log.info("添加卡片 {}", ill.getId());
            String card = getIllustrationCard(ill, ngaPost.getAttachmentsMap());
            sb.append(card);
        }

        String quote = NgaPost.getQuote(sb.toString());
        ngaPost.addContent(quote).addTitle("Pixiv").addTitle("搬运bot酱");

        String send = ngaPost.send();
        log.info("发帖成功: {}", send);

        return send;
    }


    private static void copyFile(File source, File dest) throws IOException {
        if (source.getPath().equals(dest.getPath())) {
            return;
        }
        File parentFile = dest.getParentFile();
        if (!parentFile.exists()) {
            if (!parentFile.mkdirs()) {
                log.error("创建文件夹失败 {}", parentFile.getPath());
            }
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

    /**
     * 生成一个作品的分享卡片
     *
     * @param ill            作品详情
     * @param attachmentsMap 附件map
     * @return 卡片字符串
     */
    private String getIllustrationCard(Illustration ill, Map<String, String> attachmentsMap) {
        StringBuilder sb = new StringBuilder();

        String id = ill.getId();
        List<String> aNameList = new ArrayList<>();
        Set<String> keySet = attachmentsMap.keySet();
        keySet.forEach(s -> {
            if (s.equals(id) || s.contains(id + UNDERSCORE)) {
                aNameList.add(s);
            }
        });
        //排序
        aNameList.sort((o1, o2) -> {
            if (o1.equals(o2)) {
                return 0;
            }
            if (o1.compareToIgnoreCase(o2) < 0) {
                return -1;
            }
            return 1;
        });

        String wrap = NgaPost.getWrap();
        String title = ill.getTitle();
        //作者
        sb.append("作者:").append(NgaPost.getUrlCode(ill.getUserName(), ill.getUserUrl())).append(" ");
        //pid
        sb.append(ill.getId()).append(" ").append(NgaPost.getUrlCode("ToPixiv", ill.getLink())).append(wrap);
        //标题
        if (title == null || "".equals(title)) {
            sb.append("标题: ").append(title).append(wrap);
        }
        //tag
        sb.append("标签: ").append(review(ill.createSimpleTags(dataManager.getTranslationMap()))).append(wrap);
        //图片
        for (String s : aNameList) {
            String url = attachmentsMap.get(s);
            String code = NgaPost.getAttachmentCodeFromUrl(url);
            sb.append(code).append(wrap);
        }

        return NgaPost.getCollapse(title, sb.toString(), ill.getId());
    }

    /**
     * 删除审核字符串
     *
     * @param s 字符串
     * @return 审核完成字符串
     */
    private static String review(String s) {
        for (String s1 : reviewKeyword) {
            s = s.replace(s1, "");
        }
        return s.replace(",,", "");
    }
}

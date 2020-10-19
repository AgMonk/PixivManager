package com.gin.pixivmanager.fanbox;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.gin.pixivmanager.util.JsonUtil;
import com.gin.pixivmanager.util.Request;
import com.gin.pixivmanager.util.TasksUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

@Slf4j
public class FanboxDownload {
    static String url = "https://api.fanbox.cc/post.listCreator?limit=300&creatorId=";
    static String cookie = "p_ab_id=1; p_ab_id_2=3; p_ab_d_id=1331259489; _ga=GA1.2.453670194.1595292215; privacy_policy_agreement=2; FANBOXSESSID=57680761_6TbM7A4j9KZGvl702plY3YiM8QnVAmWw; _gid=GA1.2.2123714715.1603070156";
    static String referer = "https://www.fanbox.cc";
    static String rootPath = "f:/fanbox/";


    public static void main(String[] args) throws FileNotFoundException {
        String[] user = new String[]{"waero", "turisasu", "mmu2000"};
//        String[] user = new String[]{"turisasu"};
        List<String> fileList = new ArrayList<>();
        File psdFiles = new File("f:/fanbox/psdFiles.txt");
        PrintWriter pw = new PrintWriter(psdFiles);
        pw.println(cookie);

        for (String username : user) {
            String result = Request.create(url + username)
                    .setCookie(cookie)
                    .setReferer(referer)
                    .setOrigin(referer)
                    .get().getResult();
            JSONObject json = JSONObject.parseObject(result);
            JsonUtil.printJson(json);
            JSONArray items = json.getJSONObject("body").getJSONArray("items");
            log.info("用户 {} 有 {} 个作品", username, items.size());
            items.forEach(item -> {
                JSONObject card = (JSONObject) item;
                String title = card.getString("title").trim();
                String type = card.getString("type");
                String id = card.getString("id");
                boolean isFile = "file".equals(type);
                boolean isArticle = "article".equals(type);
                boolean isImage = "image".equals(type);
                JSONObject body = card.getJSONObject("body");
                if (body != null) {
                    if (isFile || isImage) {
                        JSONArray array = isFile ? body.getJSONArray("files") : body.getJSONArray("images");
                        if (array != null) {
                            for (int i = 0; i < array.size(); i++) {
                                JSONObject file = array.getJSONObject(i);
                                String name = isFile ? file.getString("name") : String.valueOf(i);
                                name = name.length() == 1 ? "0" + name : name;
                                String extension = file.getString("extension");
                                String url = isFile ? file.getString("url") : file.getString("originalUrl");
                                String filePath = rootPath + username + "/[" + id + "]" + title + "/" + name + "." + extension;

                                if (url.endsWith("zip") || url.endsWith("psd")) {
                                    String path = filePath.substring(0, filePath.lastIndexOf("/"));
                                    pw.println(url);
                                } else {
                                    fileList.add(filePath + ">>" + url);
                                }

                            }
                        }
                    }
                    if (isArticle) {
                        JSONObject imageMap = body.getJSONObject("imageMap");
                        int index = 0;
                        imageMap.forEach((s, o) -> {
                            JSONObject file = (JSONObject) o;
                            String extension = file.getString("extension");
                            String url = file.getString("originalUrl");
                            String filePath = rootPath + username + "/[" + id + "]" + title + "/" + s + "." + extension;
                            fileList.add(filePath + ">>" + url);
//                            System.err.println(filePath + ">>" + url);
                        });

                        JSONObject fileMap = body.getJSONObject("fileMap");
                        fileMap.forEach((s, o) -> {
                            JSONObject file = (JSONObject) o;
                            String url = file.getString("url");
                            pw.println(url);
                        });
                    }
                }
            });
        }

        pw.flush();
        pw.close();

        int size = fileList.size();
        log.info("列表请求完毕 总计 {} 个文件", size);

        fileList.sort((o1, o2) -> {
            String s1 = o1.substring(o1.indexOf("["), o1.indexOf(">>"));
            int id1 = Integer.parseInt(s1.substring(s1.indexOf("[") + 1, s1.indexOf("]")));
            String s2 = o2.substring(o2.indexOf("["), o2.indexOf(">>"));
            int id2 = Integer.parseInt(s2.substring(s2.indexOf("[") + 1, s2.indexOf("]")));
            if (id1 > id2) {
                return -1;
            } else if (id1 < id2) {
                return 1;
            } else {
                return s1.compareTo(s2);
            }
        });


        CountDownLatch latch = new CountDownLatch(size);

        ThreadPoolTaskExecutor executor = TasksUtil.getExecutor("fanbox", 5);

        for (int i = 0; i < size; i++) {
            String[] s = fileList.get(i).split(">>");
            String filePath = s[0];
            String url = s[1];


            executor.execute(() -> {
                log.info("开始下载 {} >> {}", filePath, url);
                long start = System.currentTimeMillis();
                Request.create(url)
                        .setFile(new File(filePath))
                        .setCookie(cookie)
                        .get();
                long end = System.currentTimeMillis();
                log.info("下载完毕 耗时: {} 秒", (end - start) / 1000);
                latch.countDown();
                log.info("总进度: {}/{}", size - latch.getCount(), size);
            });

        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.err.println("全部下载完毕");

    }
}

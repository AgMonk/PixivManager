package com.gin.pixivmanager.fanbox;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.gin.pixivmanager.util.Request;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class FanboxDownload {
    static String url = "https://api.fanbox.cc/post.listCreator?limit=300&creatorId=";
    static String cookie = "p_ab_id=1; p_ab_id_2=3; p_ab_d_id=1331259489; _ga=GA1.2.453670194.1595292215; privacy_policy_agreement=2; FANBOXSESSID=57680761_6TbM7A4j9KZGvl702plY3YiM8QnVAmWw; _gid=GA1.2.2123714715.1603070156";
    static String referer = "https://www.fanbox.cc";


    public static void main(String[] args) {
        String username = "waero";
//        String username = "turisasu";
//        String username = "mmu2000";
        List<String> fileList = new ArrayList<>();
        String result = Request.create(url + username)
                .setCookie(cookie)
                .setReferer(referer)
                .setOrigin(referer)
                .get().getResult();
        JSONObject json = JSONObject.parseObject(result);
        JSONArray items = json.getJSONObject("body").getJSONArray("items");
        items.forEach(item -> {
            JSONObject card = (JSONObject) item;
            String title = card.getString("title");
            String type = card.getString("type");
            String id = card.getString("id");
            boolean isFile = "file".equals(type);
            JSONObject body = card.getJSONObject("body");
            if (body != null) {
                JSONArray array = isFile ? body.getJSONArray("files") : body.getJSONArray("images");
                if (array != null) {
                    for (int i = 0; i < array.size(); i++) {
                        JSONObject file = array.getJSONObject(i);
                        String name = isFile ? file.getString("name") : String.valueOf(i);
                        name = name.length() == 1 ? "0" + name : name;
                        String extension = file.getString("extension");
                        String url = isFile ? file.getString("url") : file.getString("originalUrl");
                        String filePath = "h:/fanbox/" + username + "/[" + id + "]" + title + "/" + name + "." + extension;

                        fileList.add(filePath + ">>" + url);


                    }
                }
            }
        });
        log.info("列表请求完毕 总计 {} 个文件", fileList.size());
        for (int i = 0; i < fileList.size(); i++) {
            String[] s = fileList.get(i).split(">>");
            String filePath = s[0];
            String url = s[1];

            //下载
            log.info("开始下载 {} >> {}", filePath, url);
            long start = System.currentTimeMillis();
            Request.create(url)
                    .setFile(new File(filePath))
                    .setCookie(cookie)
                    .get();
            long end = System.currentTimeMillis();
            log.info("下载完毕 耗时: {} 秒", (end - start) / 1000);
            log.info("下载总进度 {}/{}", i, fileList.size());
        }

    }
}

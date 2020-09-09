package com.gin.pixivmanager.entity;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.luhuiguo.chinese.ChineseUtils;
import lombok.Data;

import java.util.*;

@Data
public class Illustration {
    Long lastUpdate = System.currentTimeMillis();
    /**
     * 作品pid
     */
    String id;
    /**
     * 作者id
     */
    String userId;
    /**
     * 标题
     */
    String title;
    /**
     * 作者名
     */
    String userName;
    /**
     * 描述
     */
    String description;
    /**
     * 字符串格式的tag列表
     */
    String tag;
    /**
     * 字符串格式的tag对应翻译列表
     */
    String tagTranslated;
    /**
     * url前缀
     */
    String urlPrefix;
    /**
     * 文件名
     */
    String fileName;
    /**
     * 作品数量
     */
    Integer pageCount;
    /**
     * illustType=0 插画  illustType=1 漫画 illustType=2 动图
     */
    Integer illustType;
    /**
     * 收藏数
     */
    Integer bookmarkCount;
    /**
     * 是否已收藏
     */
    Integer bookmarkData;

    final static String[] USERNAME_TRASH = new String[]{"@", "＠", "|", "FANBOX", "fanbox", "仕事", "■"};
    final static Map<String, String> illegalChar = new HashMap<>();

    static {
        illegalChar.put(":", "：");
        illegalChar.put("\n", "");
        illegalChar.put("?", "？");
        illegalChar.put("<", "《");
        illegalChar.put(">", "》");
        illegalChar.put("*", "×");
        illegalChar.put("|", "^");
        illegalChar.put("\"", "“");
        illegalChar.put("\\", "_");
        illegalChar.put("/", "~");
    }

    public Illustration() {
    }

    public Illustration(JSONObject body) {
        id = body.getString("id");
        userId = body.getString("userId");
        userName = body.getString("userName");
        title = body.getString("title");
        description = body.getString("");
        description = description != null ? description.substring(0, Math.min(4000, description.length())) : null;

        bookmarkCount = body.getInteger("bookmarkCount");
        pageCount = body.getInteger("pageCount");
        illustType = body.getInteger("illustType");
        bookmarkData = body.get("bookmarkData") != null ? 1 : 0;

        JSONObject urls = body.getJSONObject("urls");
        if (urls == null) {
            return;
        }

        String original = urls.getString("original");
        int indexOf = original.lastIndexOf("/");
        fileName = original.substring(indexOf + 1);
        urlPrefix = original.substring(0, indexOf + 1);
        //如果是动图
        if (illustType == 2) {
            urlPrefix = urlPrefix.replace("img-original", "img-zip-ugoira");
            fileName = fileName.replace("ugoira0.jpg", "ugoira1920x1080.zip");
        }

        try {
            //解析tag
            StringBuilder tagBuilder = new StringBuilder();
            StringBuilder translationBuilder = new StringBuilder();
            JSONArray tagsArray = body.getJSONObject("tags").getJSONArray("tags");
            for (int i = 0; i < tagsArray.size(); i++) {
                JSONObject tag = tagsArray.getJSONObject(i);
                String tagString = tag.getString("tag");
                JSONObject trans = tag.getJSONObject("translation");

                tagBuilder.append(tagString).append(",");
                translationBuilder.append(trans != null ? trans.getString("en") : tagString).append(",");
            }
            tag = tagBuilder.toString();
            tagTranslated = translationBuilder.toString();
        } catch (Exception ignored) {
        }

        //截断用户名中的垃圾信息
        for (String trash : USERNAME_TRASH) {
            userName = userName.contains(trash) ? userName.substring(0, userName.indexOf(trash)) : userName;
        }
    }

    public String getLink() {
        return "https://www.pixiv.net/artworks/" + id;
    }

    public List<String> getUrls() {
        List<String> list = new ArrayList<>();
        for (int i = 0; i < pageCount; i++) {
            String name = fileName.replace("_p0", "_p" + i);
            list.add(urlPrefix + "/" + name);
        }
        return list;
    }

    public List<Tag> getTagList() {
        List<Tag> tagList = new ArrayList<>();
        String[] tagArray = tag.split(",");
        String[] transArray = tagTranslated.split(",");
        for (int i = 0; i < tagArray.length; i++) {
            tagList.add(new Tag(tagArray[i], transArray[i]));
        }
        return tagList;
    }

    /**
     * 生成简单文件名
     *
     * @param dic 翻译字典
     * @return 文件名
     */
    public String createSimpleName(Map<String, String> dic) {
        if (dic == null || dic.get(id) == null) {
            return fileName;
        }
        return null;
    }

    /**
     * 生成精简tag
     *
     * @param dic
     * @return
     */
    public String createSimpleTags(Map<String, String> dic) {
        /*
            步骤
            按逗号分隔，翻译一次，删除中英文右括号，把中英文左括号 斜杠 替换为逗号再次分隔 翻译 转为简体
         */
        String[] tagsArray = tag.split(",");
        Set<String> set = new HashSet<>();
        for (int i = 0; i < tagsArray.length; i++) {
            String t = translate(tagsArray[i], dic)
                    .replace(")", "")
                    .replace("）", "")
                    .replace("(", ",")
                    .replace("（", ",")
                    .replace(" ", "");

            if (t.indexOf("/") != t.lastIndexOf("/")) {
                //有多个斜杠
                t = t.replace("/", ",");
            }
            if (!t.contains(",")) {
                set.add(toSimplified(t));
            } else {
                String[] split = t.split(",");
                for (String s : split) {
                    set.add(toSimplified(translate(s, dic)));
                }
            }
        }
        StringBuilder newTag = new StringBuilder();
        for (String s : set) {
            newTag.append(s).append(",");
        }
        return newTag.substring(0, newTag.length() - 1);
    }

    private static String translate(String tag, Map<String, String> dic) {
        String s = dic.get(tag.toLowerCase());
        String t = s != null ? s : tag;
        return t;
    }

    private static String toSimplified(String s) {
        return ChineseUtils.toSimplified(s);
    }


    /**
     * 替换文件名非法字符
     *
     * @param s 输入字符串
     * @return 输出字符串
     */
    private static String clean(String s) {
        for (Map.Entry<String, String> entry : illegalChar.entrySet()) {
            s = s.replace(entry.getKey(), entry.getValue());
        }
        return s;
    }


    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("Illustration{");
        sb.append("lastUpdate=").append(lastUpdate);
        sb.append(", id='").append(id).append('\'');
        sb.append(", userId='").append(userId).append('\'');
        sb.append(", title='").append(title).append('\'');
        sb.append(", userName='").append(userName).append('\'');
        sb.append(", description='").append(description).append('\'');
        sb.append(", tag='").append(tag).append('\'');
        sb.append(", tagTranslated='").append(tagTranslated).append('\'');
        sb.append(", urlPrefix='").append(urlPrefix).append('\'');
        sb.append(", fileName='").append(fileName).append('\'');
        sb.append(", pageCount=").append(pageCount);
        sb.append(", illustType=").append(illustType);
        sb.append(", bookmarkCount=").append(bookmarkCount);
        sb.append(", bookmarkData=").append(bookmarkData);
        sb.append('}');
        return sb.toString();
    }
}

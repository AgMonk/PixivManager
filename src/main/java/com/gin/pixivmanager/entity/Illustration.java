package com.gin.pixivmanager.entity;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.luhuiguo.chinese.ChineseUtils;
import lombok.Data;

import java.util.*;

@Data
public class Illustration {
    /**
     * illustType=0 插画  illustType=1 漫画 illustType=2 动图
     */
    public final static int ILLUST_TYPE_ILLUSTRATION = 0;
    public final static int ILLUST_TYPE_MANGA = 1;
    public final static int ILLUST_TYPE_GIF = 2;

    Long lastUpdate;
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
    Integer illustType;
    /**
     * 收藏数
     */
    Integer bookmarkCount;
    /**
     * 是否已收藏
     */
    Integer bookmarkData;
    /**
     * 归档文件名
     */
    String formatName;

    final static String[] USERNAME_TRASH = new String[]{"@", "＠", "|", "FANBOX", "fanbox", "仕事", "■"};
    final static Map<String, String> ILLEGAL_CHAR = new HashMap<>();

    static {
        ILLEGAL_CHAR.put(":", "：");
        ILLEGAL_CHAR.put("\n", "");
        ILLEGAL_CHAR.put("?", "？");
        ILLEGAL_CHAR.put("<", "《");
        ILLEGAL_CHAR.put(">", "》");
        ILLEGAL_CHAR.put("*", "×");
        ILLEGAL_CHAR.put("|", "^");
        ILLEGAL_CHAR.put("\"", "“");
        ILLEGAL_CHAR.put("\\", "_");
        ILLEGAL_CHAR.put("/", "~");
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
        lastUpdate = System.currentTimeMillis();

        JSONObject urls = body.getJSONObject("urls");
        if (urls == null) {
            return;
        }

        String original = urls.getString("original");
        int indexOf = original.lastIndexOf("/");
        fileName = original.substring(indexOf + 1);
        urlPrefix = original.substring(0, indexOf + 1);
        //如果是动图
        if (illustType == ILLUST_TYPE_GIF) {
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
        urlPrefix += urlPrefix.endsWith("/") ? "" : "/";
        if (illustType == ILLUST_TYPE_GIF) {
            list.add(urlPrefix + fileName);
        } else {
            for (int i = 0; i < pageCount; i++) {
                String name = fileName.replace("_p0", "_p" + i);
//                String name = id + "_p" + i;
                list.add(urlPrefix + name);
            }
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

    public String createSimpleName(Integer count) {
        return id + "_p" + count + fileName.substring(fileName.lastIndexOf("."));
    }

    /**
     * 生成简单文件名
     *
     * @param dic 翻译字典
     * @return 文件名
     */
    public String createFormatName(Map<String, String> dic) {
        if (dic == null) {
            return fileName;
        }
        StringBuilder builder = new StringBuilder();

        builder.append(illustType).append("/");

        addBrackets(builder, "userId", userId, "65535");
        addBrackets(builder, "u", clean(userName), "NullName");
        builder.append("/");
        addBrackets(builder, "bmk", bookmarkCount, 65535);
        addBrackets(builder, "", id + "_p{count}", null);
        addBrackets(builder, "ti", clean(title), null);
        addBrackets(builder, "tags", clean(createSimpleTags(dic)), null);

        //后缀名
        builder.append(fileName.substring(fileName.lastIndexOf('.')));

        formatName = builder.toString();
        return formatName;
    }

    /**
     * 生成精简tag
     *
     * @param dic 字典
     * @return tag字符串
     */
    public String createSimpleTags(Map<String, String> dic) {
        /*
            步骤
            按逗号分隔，翻译一次，删除中英文右括号，把中英文左括号 斜杠 替换为逗号再次分隔 翻译 转为简体
         */
        String[] tagsArray = tag.split(",");
        Set<String> set = new HashSet<>();
        for (String value : tagsArray) {
            String t = translate(value, dic)
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
        return s != null ? s : tag;
    }

    private static String toSimplified(String s) {
        return ChineseUtils.toSimplified(s);
    }

    private static void addBrackets(StringBuilder builder, String field, Object value, Object defaultValue) {
        if (value == null) {
            if (defaultValue == null) {
                return;
            } else {
                value = defaultValue;
            }
        }
        builder.append("[").append(field);
        if (!"".equals(field)) {
            builder.append("_");
        }
        builder.append(value).append("]");
    }

    /**
     * 替换文件名非法字符
     *
     * @param s 输入字符串
     * @return 输出字符串
     */
    private static String clean(String s) {
        for (Map.Entry<String, String> entry : ILLEGAL_CHAR.entrySet()) {
            s = s.replace(entry.getKey(), entry.getValue());
        }
        return s;
    }

    public String getUserUrl() {
        return "https://www.pixiv.net/users/{userId}/artworks".replace("{userId}", userId);
    }

    @Override
    public String toString() {
        return "Illustration{" + "lastUpdate=" + lastUpdate +
                ", id='" + id + '\'' +
                ", userId='" + userId + '\'' +
                ", title='" + title + '\'' +
                ", userName='" + userName + '\'' +
                ", description='" + description + '\'' +
                ", tag='" + tag + '\'' +
                ", tagTranslated='" + tagTranslated + '\'' +
                ", urlPrefix='" + urlPrefix + '\'' +
                ", fileName='" + fileName + '\'' +
                ", pageCount=" + pageCount +
                ", illustType=" + illustType +
                ", bookmarkCount=" + bookmarkCount +
                ", bookmarkData=" + bookmarkData +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Illustration that = (Illustration) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

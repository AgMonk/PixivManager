package com.gin.pixivmanager.entity;

import com.luhuiguo.chinese.ChineseUtils;
import lombok.Data;

import java.util.Map;
import java.util.Objects;

@Data
public class Tag {
    Integer id, count = 0;
    /**
     * tag名称
     */
    String name;
    /**
     * 官方翻译
     */
    String translation;
    /**
     * 推荐翻译
     */
    String trans;
    final static String[] TRASH_WORDS = new String[]{"#", "*", "※"};

    public Tag(String name, String translation) {
        this.name = name.toLowerCase();
        this.translation = translation;
    }

    /**
     * 生成推荐翻译
     *
     * @param dic
     */
    public void createRecommendTranslation(Map<String, String> dic) {
        String tempName = name;
        String tempTrans = translation;
        for (Map.Entry<String, String> entry : dic.entrySet()) {
            tempName = tempName.replace(entry.getKey(), entry.getValue());
            tempTrans = tempTrans.replace(entry.getKey(), entry.getValue());
        }
        tempName = ChineseUtils.toSimplified(tempName);
        tempTrans = ChineseUtils.toSimplified(tempTrans);

        tempName = clean(tempName);
        tempTrans = clean(tempTrans);

        if (tempName.length() <= tempTrans.length()) {
            trans = tempName;
        } else {
            trans = tempTrans;
        }
    }

    private static String clean(String s) {
        for (String trashWord : TRASH_WORDS) {
            s = s.replace(trashWord, "");
        }
        return s;
    }

    public void setName(String name) {
        this.name = name.toLowerCase();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Tag tag = (Tag) o;
        return name.equals(tag.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    public void addCount() {
        count++;
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("Tag{");
        sb.append("count=").append(count);
        sb.append(", name='").append(name).append('\'');
        sb.append(", translation='").append(translation).append('\'');
        sb.append('}');
        return sb.toString();
    }
}

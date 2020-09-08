package com.gin.pixivmanager.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pixiv.url")
@Data
public class PixivUrl {
    String illustration, search, bookmarks, addTags, setTag, addBookmark, user, cookie;

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("PixivUrl{").append("\n");
        sb.append("illustration='").append(illustration).append('\'').append("\n");
        sb.append(", search='").append(search).append('\'').append("\n");
        sb.append(", bookmarks='").append(bookmarks).append('\'').append("\n");
        sb.append(", addTags='").append(addTags).append('\'').append("\n");
        sb.append(", setTag='").append(setTag).append('\'').append("\n");
        sb.append(", addBookmark='").append(addBookmark).append('\'').append("\n");
        sb.append(", user='").append(user).append('\'').append("\n");
        sb.append('}');
        return sb.toString();
    }
}

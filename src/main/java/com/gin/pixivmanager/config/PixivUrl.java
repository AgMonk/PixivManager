package com.gin.pixivmanager.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author bx002
 */
@ConfigurationProperties(prefix = "pixiv.url")
@Data
public class PixivUrl {
    String illustration, search, bookmarks, addTags, setTag, addBookmark, user, cookie;

}

package com.gin.pixivmanager.service;

import java.util.Map;

/**
 * 当前登录用户的信息
 */
public interface UserInfo {

    String getNgaCookie(String s);

    String getNgaFid(String s);

    String getNgaTid(String s);

    String getUid();

    String getCookie();

    String getTt();

    String getRootPath();

    String getArchivePath();

    /**
     * 返回用户、版面、主题名称
     *
     * @return
     */
    Map<String, Object> getInfos();
}

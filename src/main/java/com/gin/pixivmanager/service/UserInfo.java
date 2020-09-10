package com.gin.pixivmanager.service;

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
}

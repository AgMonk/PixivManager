package com.gin.pixivmanager.service;

/**
 * 当前登录用户的信息
 */
public interface UserInfo {
    String getUid();

    String getCookie();

    String getTt();

    String getRootPath();
}

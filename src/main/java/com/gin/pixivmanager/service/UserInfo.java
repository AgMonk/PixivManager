package com.gin.pixivmanager.service;

import java.util.Map;
import java.util.Set;

/**
 * 当前登录用户的信息
 *
 * @author bx002
 */
public interface UserInfo {
    /**
     * 获取nga cookie
     *
     * @param s 用户名标识
     * @return cookie
     */
    String getNgaCookie(String s);

    /**
     * 获取nga fid
     *
     * @param s 版面标识
     * @return fid
     */
    String getNgaFid(String s);

    /**
     * 获取nga tid
     *
     * @param s 帖子标识
     * @return tid
     */
    String getNgaTid(String s);

    /**
     * 获取pixiv uid
     *
     * @return uid
     */
    String getUid();

    /**
     * 获取pixiv cookie
     *
     * @return pixiv cookie
     */
    String getCookie();

    /**
     * 获取pixiv tt
     *
     * @return tt
     */
    String getTt();

    /**
     * 获取工作目录
     *
     * @return 工作目录
     */
    String getRootPath();

    /**
     * 获取归档目录
     *
     * @return 归档目录
     */
    String getArchivePath();

    Set<String> getKeywordSet();


    /**
     * 返回用户、版面、主题名称
     *
     * @return 获取nga标识
     */
    Map<String, Object> getInfos();
}

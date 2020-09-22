package com.gin.pixivmanager.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 用户自定义配置
 *
 * @author bx002
 */
@Slf4j
@Service
public class UserInfoImpl implements UserInfo {
    /**
     * pixiv用户uid
     */
    String uid;
    /**
     * pixiv cookie
     */
    String cookie;
    /**
     * pixiv tt 与cookie一一对应
     */
    String tt;
    /**
     * 下载文件的根目录
     */
    String rootPath;
    /**
     * 归档根目录
     */
    String archivePath;
    final static String PIXIV_INFO = "config/pixiv.txt";
    final static String NGA_INFO = "config/nga.txt";
    final static String KEYWORD = "config/keyword.txt";
    final static File PIXIV_FILE = new File(PIXIV_INFO);
    final static File NGA_FILE = new File(NGA_INFO);
    final static File KEYWORD_FILE = new File(KEYWORD);

    final static Map<String, String> NGA_COOKIE_MAP = new HashMap<>();
    final static Map<String, String> NGA_FID_MAP = new HashMap<>();
    final static Map<String, String> NGA_TID_MAP = new HashMap<>();
    final static Set<String> KEYWORD_LIST = new HashSet<>();

    @Override
    public String getNgaCookie(String s) {
        return NGA_COOKIE_MAP.get(s);
    }

    @Override
    public String getNgaFid(String s) {
        return NGA_FID_MAP.get(s);
    }

    @Override
    public String getNgaTid(String s) {
        return NGA_TID_MAP.get(s);
    }

    @Override
    public String getUid() {
        return uid;
    }

    @Override
    public String getCookie() {
        return cookie;
    }

    @Override

    public String getTt() {
        return tt;
    }

    @Override
    public String getRootPath() {
        return rootPath.endsWith("/") ? rootPath.substring(0, rootPath.length() - 1) : rootPath;
    }

    @Override
    public String getArchivePath() {
        return archivePath;
    }

    @Override
    public Set<String> getKeywordSet() {
        return KEYWORD_LIST;
    }

    public UserInfoImpl() {
        if (!PIXIV_FILE.exists()) {
            log.info("配置文件未找到 {}", PIXIV_INFO);
            return;
        }
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(NGA_FILE), StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] s;
                if (line.indexOf(':') != line.lastIndexOf(':')) {
                    s = line.split(":");

                    if (line.startsWith("cookie")) {
                        NGA_COOKIE_MAP.put(s[1], s[2]);
                    }
                    if (line.startsWith("fid")) {
                        NGA_FID_MAP.put(s[1], s[2]);
                    }
                    if (line.startsWith("tid")) {
                        NGA_TID_MAP.put(s[1], s[2]);
                    }
                }
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(PIXIV_FILE), StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("cookie")) {
                    cookie = line.substring(line.indexOf(":") + 1);
                }
                if (line.startsWith("tt")) {
                    tt = line.substring(line.indexOf(":") + 1);
                }
                if (line.startsWith("uid")) {
                    uid = line.substring(line.indexOf(":") + 1);
                }
                if (line.startsWith("rootPath")) {
                    rootPath = line.substring(line.indexOf(":") + 1);
                }
                if (line.startsWith("archivePath")) {
                    archivePath = line.substring(line.indexOf(":") + 1);
                }
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }


        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(KEYWORD_FILE), StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                KEYWORD_LIST.add(line);
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    @Override
    public Map<String, Object> getInfos() {
        Map<String, Object> map = new HashMap<>(3);
        map.put("user", NGA_COOKIE_MAP.keySet());
        map.put("fid", NGA_FID_MAP.keySet());
        map.put("tid", NGA_TID_MAP.keySet());
        return map;
    }

}

package com.gin.pixivmanager.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

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
    final static File pixivFile = new File(PIXIV_INFO);
    final static File ngaFile = new File(NGA_INFO);

    final static Map<String, String> ngaCookieMap = new HashMap<>();
    final static Map<String, String> ngaFidMap = new HashMap<>();
    final static Map<String, String> ngaTidMap = new HashMap<>();

    @Override
    public String getNgaCookie(String s) {
        return ngaCookieMap.get(s);
    }

    @Override
    public String getNgaFid(String s) {
        return ngaFidMap.get(s);
    }

    @Override
    public String getNgaTid(String s) {
        return ngaTidMap.get(s);
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
        String s = rootPath.endsWith("/") ? rootPath.substring(0, rootPath.length() - 1) : rootPath;
        return s;
    }

    @Override
    public String getArchivePath() {
        return archivePath;
    }

    public UserInfoImpl() {
        if (!pixivFile.exists()) {
            log.info("配置文件未找到 {}", PIXIV_INFO);
            try {
                boolean newFile = pixivFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(ngaFile), StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] s;
                if (line.indexOf(':') != line.lastIndexOf(':')) {
                    s = line.split(":");

                    if (line.startsWith("cookie")) {
                        ngaCookieMap.put(s[1], s[2]);
                    }
                    if (line.startsWith("fid")) {
                        ngaFidMap.put(s[1], s[2]);
                    }
                    if (line.startsWith("tid")) {
                        ngaTidMap.put(s[1], s[2]);
                    }
                }
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(pixivFile), StandardCharsets.UTF_8));
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

    }

    @Override
    public Map<String, Object> getInfos() {
        Map<String, Object> map = new HashMap<>();
        map.put("user", ngaCookieMap.keySet());
        map.put("fid", ngaFidMap.keySet());
        map.put("tid", ngaTidMap.keySet());
        return map;
    }

}

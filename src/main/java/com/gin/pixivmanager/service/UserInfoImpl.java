package com.gin.pixivmanager.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * 用户自定义配置
 *
 * @author bx002
 */
@Slf4j
@Service
public class UserInfoImpl implements UserInfo {
    String uid, cookie, tt, rootPath;
    final static String pathname = "config/user_info.txt";
    final static File file = new File(pathname);

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
        return rootPath;
    }

    public UserInfoImpl() {
        if (!file.exists()) {
            log.info("配置文件未找到 {}", pathname);
            try {
                boolean newFile = file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
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
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        log.info(toString());
    }

    @Override
    public String toString() {
        return "UserInfoImpl{" + "uid='" + uid + '\'' +
                ", cookie='" + cookie.substring(0, 20) + '\'' +
                ", tt='" + tt + '\'' +
                '}';
    }
}

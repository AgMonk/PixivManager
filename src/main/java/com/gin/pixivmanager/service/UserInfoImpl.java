package com.gin.pixivmanager.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;

@Slf4j
@Service
public class UserInfoImpl implements UserInfo {
    String uid, cookie, tt;
    final static String pathname = "config/user_info.txt";
    final static File file = new File(pathname);
    ;


    public UserInfoImpl() {
        if (!file.exists()) {
            log.info("配置文件未找到 {}", pathname);
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "utf-8"));
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
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        log.info(toString());
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("UserInfoImpl{");
        sb.append("uid='").append(uid).append('\'');
        sb.append(", cookie='").append(cookie.substring(0, 20)).append('\'');
        sb.append(", tt='").append(tt).append('\'');
        sb.append('}');
        return sb.toString();
    }
}

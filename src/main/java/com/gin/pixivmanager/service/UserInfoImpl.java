package com.gin.pixivmanager.service;

import org.springframework.stereotype.Service;

import java.io.File;

@Service
public class UserInfoImpl implements UserInfo {
    String uid, cookie, tt;
    final static File file = new File("config/user_info.txt");


}

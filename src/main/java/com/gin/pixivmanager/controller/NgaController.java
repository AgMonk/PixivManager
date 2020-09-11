package com.gin.pixivmanager.controller;

import com.gin.pixivmanager.service.DataManager;
import com.gin.pixivmanager.service.NgaPostServ;
import com.gin.pixivmanager.service.UserInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("nga")
public class NgaController {
    final NgaPostServ ngaPostServ;
    final DataManager dataManager;
    final UserInfo userInfo;

    public NgaController(NgaPostServ ngaPostServ, DataManager dataManager, UserInfo userInfo) {
        this.ngaPostServ = ngaPostServ;
        this.dataManager = dataManager;
        this.userInfo = userInfo;
    }

    @RequestMapping("repost")
    public void repost(HttpServletResponse response, String f, String t, String... name) throws IOException {
        String repost = ngaPostServ.repost(f, t, name);
        response.sendRedirect(repost);
    }

    @RequestMapping("getInfo")
    public Map<String, Object> getInfo() {
        return userInfo.getInfos();
    }

}

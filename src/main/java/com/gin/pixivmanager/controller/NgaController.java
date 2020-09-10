package com.gin.pixivmanager.controller;

import com.gin.pixivmanager.service.DataManager;
import com.gin.pixivmanager.service.NgaPostServ;
import com.gin.pixivmanager.service.UserInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

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
    public void repost(HttpServletResponse response, String... id) throws IOException {
        String repost = ngaPostServ.repost(id);
        response.sendRedirect(repost);
    }
}

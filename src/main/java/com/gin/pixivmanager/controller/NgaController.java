package com.gin.pixivmanager.controller;

import com.gin.pixivmanager.service.NgaPostServ;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.util.Map;

@RestController
@RequestMapping("nga")
public class NgaController {
    final NgaPostServ ngaPostServ;

    public NgaController(NgaPostServ ngaPostServ) {
        this.ngaPostServ = ngaPostServ;
    }

    @RequestMapping("test")
    public void test() {
        Map<String, File> map = ngaPostServ.prepare4Files("84256106", "84256700", "84276873");

        map.forEach((s, file) -> System.err.println(s + " " + file));
    }
}

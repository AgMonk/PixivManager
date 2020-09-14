package com.gin.pixivmanager.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @author bx002
 */
@RestController
public class HelloController {

    @RequestMapping("")
    public void hello(HttpServletResponse response) {
        try {
            response.sendRedirect("/index.html");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}

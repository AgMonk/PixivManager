package com.gin.pixivmanager.config;

import com.gin.pixivmanager.service.UserInfo;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;

/**
 * 配置资源路径
 *
 * @author bx002
 */
@Configuration
public class MyUrlPatternConfiguration extends WebMvcConfigurationSupport {
    final UserInfo userInfo;

    public MyUrlPatternConfiguration(UserInfo userInfo) {
        this.userInfo = userInfo;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        //硬盘文件目录
        registry.addResourceHandler("/pixiv/**").addResourceLocations("file:" + userInfo.getRootPath() + "/");

        registry.addResourceHandler("/**").addResourceLocations("classpath:/static/");
        super.addResourceHandlers(registry);
    }
}

package com.gin.pixivmanager.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;

/**
 * @author bx002
 */
public class JsonUtil {

    public static void printJson(Object obj) {
        System.err.println(prettyJson(obj));
    }

    public static String prettyJson(Object obj) {
        return JSON.toJSONString(obj, SerializerFeature.PrettyFormat, SerializerFeature.WriteMapNullValue,
                SerializerFeature.WriteDateUseDateFormat);
    }
}

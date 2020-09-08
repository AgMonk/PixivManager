package com.gin.pixivmanager.entity;

import java.util.HashMap;
import java.util.Map;

/**
 * ParmaMap工厂
 */
public class ParamMapBuilder {
    Map<String, String[]> map = new HashMap<>();

    public static ParamMapBuilder create() {
        return new ParamMapBuilder();
    }

    public ParamMapBuilder add(String k, Object v) {
        map.put(k, new String[]{String.valueOf(v)});
        return this;
    }

    public Map<String, String[]> build() {
        return map;
    }
}

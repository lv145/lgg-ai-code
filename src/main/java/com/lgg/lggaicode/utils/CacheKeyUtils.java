package com.lgg.lggaicode.utils;


import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import org.springframework.util.DigestUtils;

/**
 * 缓存键工具类
 */
public class CacheKeyUtils {
    /**
     * 根据对象生成缓存键(将对象转换为JSON字符串,并使用MD5加密)
     * @param obj 对象
     * @return 缓存键
     */
    public static String generateKey(Object obj){
        if(obj == null){
            return DigestUtil.md5Hex("null");
        }
        String jsonStr = JSONUtil.toJsonStr(obj);
        return DigestUtil.md5Hex(jsonStr);

    }
}

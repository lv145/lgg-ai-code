package com.lgg.lggaicode.config;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.Resource;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Redis缓存管理配置类
 */
@Configuration
public class RedisCacheManageConfig {
    @Resource
    private RedisConnectionFactory redisConnectionFactory;;

    @Bean
    public CacheManager cacheManager() {
        //配置ObjectMapper支撑 JAVA8时间类型
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))   //缓存过期时间为30分钟
                .disableCachingNullValues()         //禁用缓存null值
                .serializeKeysWith(     //key使用String序列化器
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(new StringRedisSerializer())
//                .serializeValuesWith(   //value使用JSON序列化器
//                        RedisSerializationContext.SerializationPair //会出现redis反序列化异常,因为redis不会缓存的对象类型信息丢失
//                                .fromSerializer(new GenericJackson2JsonRedisSerializer(objectMapper))
                );
        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaultConfig)
                .withCacheConfiguration("good_app_page",
                        defaultConfig.entryTtl(Duration.ofMinutes(5))).build();
    }
}

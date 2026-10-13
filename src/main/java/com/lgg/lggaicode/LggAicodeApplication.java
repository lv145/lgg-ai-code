package com.lgg.lggaicode;

import dev.langchain4j.community.store.embedding.redis.spring.RedisEmbeddingStoreAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

//@SpringBootApplication
@MapperScan("com.lgg.lggaicode.mapper")
@EnableAspectJAutoProxy(exposeProxy = true)
//排除RedisEmbeddingStoreAutoConfiguration的自动装配否则会报错
@SpringBootApplication(exclude = {RedisEmbeddingStoreAutoConfiguration.class})
@EnableCaching //开启缓存功能
public class LggAicodeApplication {

    public static void main(String[] args) {
        SpringApplication.run(LggAicodeApplication.class, args);
    }

}

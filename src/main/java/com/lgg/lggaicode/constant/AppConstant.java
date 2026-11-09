package com.lgg.lggaicode.constant;

import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;

import java.io.File;

/**
 * 应用常量
 */
public interface AppConstant {

    /**
     * 用户分页查询每页最大数量
     */
    int MAX_PAGE_SIZE = 20;

    /**
     * 默认应用名称
     */
    String DEFAULT_APP_NAME = "未命名应用";
    //输出内容根目录
    String OUTPUT_ROOT_DIR = "/www/wwwroot/118.31.112.155/output/";
    // 代码输出根目录
    String CODE_OUTPUT_ROOT_DIR = OUTPUT_ROOT_DIR+"code_output";
    // 应用部署目录
    String CODE_DEPLOY_ROOT_DIR = OUTPUT_ROOT_DIR+"code_deploy";

    /**
     * 应用部署域名
     */
    String CODE_DEPLOY_HOST = "http://localhost";
    String VUE_PROJECT_PREFIX = "vue_project_";
}

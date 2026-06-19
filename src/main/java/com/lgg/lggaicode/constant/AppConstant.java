package com.lgg.lggaicode.constant;

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

    /**
     * 应用生成目录
     */
    String CODE_OUTPUT_ROOT_DIR = System.getProperty("user.dir") + "/tmp/code_output";

    /**
     * 应用部署目录
     */
    String CODE_DEPLOY_ROOT_DIR = System.getProperty("user.dir") + "/tmp/code_deploy";

    /**
     * 应用部署域名
     */
    String CODE_DEPLOY_HOST = "http://localhost";

}

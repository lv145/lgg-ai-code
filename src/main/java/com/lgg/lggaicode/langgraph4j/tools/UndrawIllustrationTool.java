package com.lgg.lggaicode.langgraph4j.tools;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.lgg.lggaicode.langgraph4j.state.ImageCategoryEnum;
import com.lgg.lggaicode.langgraph4j.state.ImageResource;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.Arrays;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class UndrawIllustrationTool {

    private static final String UNDRAW_API_URL = "https://undraw.co/_next/data/nS41BRGVYK4TTVjGNap_q/search/%s.json?term=%s";

    @Tool("搜索插画图片，用于网站美化和装饰")
    public List<ImageResource> searchIllustrations(@P("搜索关键词,关键词不要用中文") String query) {
        List<ImageResource> imageList = new ArrayList<>();
        int searchCount = 12;
//        String[] queryArray = query.split(" ");
//        if (Arrays.isNullOrEmpty(queryArray)||query.isBlank()) {
//            return imageList;
//        }
//        for (String queryStr : queryArray) { //ai出现多关键词并用空格分割
            String apiUrl = String.format(UNDRAW_API_URL, query, query);
            log.info("apiUrl+"+apiUrl);
            // 使用 try-with-resources 自动释放 HTTP 资源
            try (HttpResponse response = HttpRequest.get(apiUrl).timeout(10000).execute()) {
                if (!response.isOk()) {
                    return imageList;
                }
                JSONObject result = JSONUtil.parseObj(response.body());
                JSONObject pageProps = result.getJSONObject("pageProps");
                if (pageProps == null) {
                    return imageList;
                }
                JSONArray initialResults = pageProps.getJSONArray("initialResults");
                if (initialResults == null || initialResults.isEmpty()) {
                    return imageList;
                }
                int count = 3;
                int actualCount = Math.min(count, initialResults.size());
                for (int i = 0; i < actualCount; i++) {
                    JSONObject illustration = initialResults.getJSONObject(i);
                    String title = illustration.getStr("title", "插画");
                    String media = illustration.getStr("media", "");
                    if (StrUtil.isNotBlank(media)) {
                        imageList.add(ImageResource.builder()
                                .category(ImageCategoryEnum.ILLUSTRATION)
                                .description(title)
                                .url(media)
                                .build());
                        if (imageList.size()==searchCount){
                            return imageList;
                        }
                    }
                }
            } catch (Exception e) {
                log.error("搜索插画失败：{}", e.getMessage(), e);
            }
//        }
        return imageList;
    }
}

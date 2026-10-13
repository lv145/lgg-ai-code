package com.lgg.lggaicode.langgraph4j.tools;

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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class ImageSearchTool {
    public static final String PEXELS_API_URL="https://api.pexels.com/v1/search";

    @Value("${pexels.api-key}")
    private String pexelsApiKey;

    @Tool
    public List<ImageResource> searchContentImages(@P("搜索关键词,若有多个关键词,请使用空格分割")String query) {
        List<ImageResource> imageList = new ArrayList<>();
        int searchCount = 12;

        //调用API,注意释放资源
        try (HttpResponse response = HttpRequest.get(PEXELS_API_URL)
                .header("Authorization", pexelsApiKey)
                .form("query", query)
                .form("per_page", searchCount)
                .form("page", 1).execute()) {
            if (response.isOk()){
                JSONObject result = JSONUtil.parseObj(response.body());
                JSONArray photos = result.getJSONArray("photos");
                for (int i = 0; i < photos.size(); i++) {
                    JSONObject photo = photos.getJSONObject(i);
                    JSONObject src = photo.getJSONObject("src");
                    imageList.add(ImageResource.builder()
                            .category(ImageCategoryEnum.CONTENT)
                            .description(photo.getStr("alt",query))
                            .url(src.getStr("medium")).build());
                }
            }
        }catch (Exception e){log.error("Pexels API 调用失败:{}", e.getMessage(),e);
        }
        return imageList;
    }

}

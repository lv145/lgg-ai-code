package com.lgg.lggaicode.ai.tools;

import cn.hutool.json.JSONObject;
import com.lgg.lggaicode.constant.AppConstant;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import dev.langchain4j.service.MemoryId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * 文件修改工具
 * 支持AI通过工具调用的方式修改文件内容
 */
@Slf4j
@Component
public class FileModifyTool  extends BaseTool {
    @Override
    public String getToolName() {
        return "modifyFile";
    }

    @Override
    public String getDisplayName() {
        return "修改文件";
    }

    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        String relativeFilePath = arguments.getStr("relativeFilePath");
        String oldContent = arguments.getStr("oldContent");
        String newContent = arguments.getStr("newContent");
        return String.format("""
                [工具调用] %s : %s
                替换内容:
                ```
                %s
                ```
                新内容:
                ```
                %s
                ```
                """, getDisplayName(), relativeFilePath,oldContent,newContent);
    }

    /**
     * 修改文件内容,用新内容替换指定的旧内容
     */
    @Tool("修改文件内容,用新内容替换指定的旧内容")
    public String modifyFile(
            @P("需要修改文件的相对路径")
            String relativeDirPath,
                             @P("要替换的旧内容")
                             String oldContent,
                             @P("替换后的新内容")
                             String newContent,
                             @ToolMemoryId Long appId)
    {
        try {
            Path path = Paths.get(relativeDirPath);
            if (!path.isAbsolute()){
                String projectDirName= "vue_project_" + appId;
                Path projectRootPath = Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR, projectDirName);
                path=projectRootPath.resolve(path);
            }
            if (!Files.exists(path)||!Files.isRegularFile(path)){
                return "错误:文件不存在或不是文件-" + relativeDirPath;
            }
            String originalContent = Files.readString(path);
            if (!originalContent.contains(oldContent)){
                return "警告:文件中未找到要替换的内容,文件未修改-" + oldContent;
            }
            String modifiedContent = originalContent.replace(oldContent, newContent);
            Files.writeString(path, modifiedContent, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.info("成功修改文件:{}", path.toAbsolutePath());
            return "文件修改成功-" + relativeDirPath;
        } catch (IOException e) {
            String errorMessage = "错误:修改文件失败-" + relativeDirPath+",错误信息:"+e.getMessage();
            log.error(errorMessage);
            return errorMessage;
        }
    }

    public static void main(String[] args) {
        FileModifyTool tool = new FileModifyTool();
        tool.modifyFile("src/pages/Home.vue",
                "    <button class=\"btn\" @click=\"add\">添加</button>",
                "    <button class=\"btn\" @click=\"add\">添加任务</button>",
                1L);
    }
}

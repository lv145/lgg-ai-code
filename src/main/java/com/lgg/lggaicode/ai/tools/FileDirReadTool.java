package com.lgg.lggaicode.ai.tools;


import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONObject;
import com.lgg.lggaicode.constant.AppConstant;
import com.lgg.lggaicode.utils.DirectoryTreePrinterUtils;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import dev.langchain4j.service.MemoryId;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 文件目录读取工具
 */
@Slf4j
@Component
public class FileDirReadTool  extends BaseTool {
    @Override
    public String getToolName() {
        return "readDir";
    }

    @Override
    public String getDisplayName() {
        return "读取目录";
    }

    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        String relativeDirPath = arguments.getStr("relativeDirPath");
        relativeDirPath = relativeDirPath == null ? "根目录" : relativeDirPath;
        return String.format("[工具调用] %s : %s", getDisplayName(), relativeDirPath);
    }

    /**
     * 需要忽略的文件和目录
     */
    private static final Set<String> IGNORE_NAMES = Set.of(
            ".git", ".idea", ".vscode", "target", "build", "coverage",
            "node_modules", "dist", ".mvn", ".env", ".DS_Store"
    );
    /**
     * 需要忽略的文件扩展名
     */
    private static final Set<String> IGNORE_EXTENSIONS =
            Set.of(".log", ".tmp", ".cache", ".lock");
    @Tool("读取目录结构,获取指定目录下的所有文件和子目录信息")
    public String readDir(
            @P("目录的相对路径,为空则读取整个项目结构")
            String relativeDirPath,
            @ToolMemoryId Long appId) {
        try{

            relativeDirPath = relativeDirPath == null ? "" : relativeDirPath;
            Path path = Paths.get(relativeDirPath);
            if (!path.isAbsolute()) {

                String projectDirName="vue_project_"+appId;
                Path projectRoot = Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR, projectDirName);
                path=projectRoot.resolve(relativeDirPath); // 转换为绝对路径
                log.info("relativeDirPath: {},path: {}", relativeDirPath,path);
            }
            File targetFile = path.toFile();
            if (!targetFile.exists()||!targetFile.isDirectory()) {
                return "目录不存在或不是目录";
            }
            StringBuilder structure = new StringBuilder();
            structure.append("项目目录结构:\n");
            // 使用Hutool递归所有文件并过滤忽略文件
//            List<File> allFiles = FileUtil.loopFiles(targetFile, file -> !shouldIgnore(file.getAbsolutePath()));
//
//            //按路径深度和名称排序显示
//            allFiles.stream()
//                    .sorted((f1,f2)->{
//                        int depth1=getRelativeDepth(targetFile,f1);
//                        int depth2=getRelativeDepth(targetFile,f2);
//                        if (depth1!=depth2) {
//                            // 深度不同时,按深度排序
//                            return Integer.compare(depth1,depth2);
//                        }
//                        // 深度相同时,按路径排序
//                        return f1.getPath().compareTo(f2.getPath());
//                    })
//                    .forEach(file -> {
//                            int depth=getRelativeDepth(targetFile,file);
//                            //根据深度添加空格
//                            String indent="-".repeat(depth);
//                            structure.append(indent).append(file.getName()).append("\n");
//                        }
//                    );
            String dirTree = DirectoryTreePrinterUtils.directoryTree(targetFile.getPath(), file -> !shouldIgnore(file.getName()));
            structure.append(dirTree);
            return structure.toString();

        }catch (Exception e){
            String errorMessage="读取目录结构失败:"+relativeDirPath+",错误信息:"+e.getMessage();
            log.error(errorMessage);
            return errorMessage;
        }

    }

    /**
     * 计算文件相对与根目录的深度
     * @param root 根目录
     * @param file 文件
     * @return 相对深度,从0开始
     */
    private int getRelativeDepth(File root ,File file) {
        Path rootPath= root.toPath();
        Path filePath= file.toPath();
        return rootPath.relativize(filePath).getNameCount()-1;
    }

//    /**
//     * 检查文件名是否需要忽略
//     * @param fileName
//     * @return
//     */
//    private  boolean shouldIgnore(String fileName) {
//        // 匹配忽略名称（目录/文件都生效，比如 node_modules、.git）
//        if (IGNORE_NAMES.contains(fileName)) {
//            return true;
//        }
//        // 匹配忽略后缀（只针对文件）
//        return IGNORE_EXTENSIONS.stream().anyMatch(fileName::endsWith);
//    }
    /**
     * 检查当前文件/目录是否需要忽略
     * @param path 文件路径
     * @return true = 忽略，false = 保留
     */
    private boolean shouldIgnore(String path) {

        // 匹配忽略名称（目录/文件都生效，比如 node_modules、.git）
        if (IGNORE_NAMES.stream().anyMatch(path::contains)) {
            return true;
        }
        // 匹配忽略后缀（只针对文件）
        return IGNORE_EXTENSIONS.stream().anyMatch(path::endsWith);
    }
}

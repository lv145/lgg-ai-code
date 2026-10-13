package com.lgg.lggaicode.langgraph4j.model;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class ImageCollectionPlan  implements Serializable
{
    /**
     * 内容图片搜索任务列表
     */
    private List<ImageSearchTask> contentImageTasks;
    /**
     * 插画图片搜索任务列表
     */
    private List<IllustrationTask> illustrationTasks;

    /**
     * 架构图生成任务列表
     */
    private List<DiagramTask> diagramTasks;


    /**
     * Logo生成任务列表
     */
    private List<LogoTasks> logoTasks;

    /**
     * 内容图片搜索任务
     * 对应 ImageSearchTool.searchContentImages(String query)
     */
    public record ImageSearchTask(String query) implements Serializable{

    }
    /**
     * 插画图片搜索任务
     * 对应 ImageSearchTool.searchIllustrations(String query)
     */
    public record IllustrationTask(String query) implements Serializable{

    }
    /**
     * 架构图生成任务
     * 对应 MermaidDiagramTool.generateMermaidDiagram(String mermaidCode, String description)
     */
    public record DiagramTask(String mermaidCode,String description) implements Serializable{

    }
    /**
     * Logo生成任务
     * 对应 LogoGeneratorTool.generateLogo(String description)
     */
    public record LogoTasks(String description) implements Serializable{

    }

}

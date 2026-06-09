package com.lgg.lggaicode.parser;

import com.lgg.lggaicode.exception.BusinessException;
import com.lgg.lggaicode.exception.ErrorCode;
import com.lgg.lggaicode.model.enums.CodeGenTypeEnum;

/**
 * 代码解析器执行器
 */
public class CodeParserExecutor {
    private static  final  HtmlCodeParser htmlCodeParser=new HtmlCodeParser();
    private static  final  MultiFileCodeParser multiFileCodeParser=new MultiFileCodeParser();
    /**
     *
     */
    public static Object executeParser(String codeContent, CodeGenTypeEnum codeGenTypeEnum) {
        return switch (codeGenTypeEnum) {
            case HTML -> htmlCodeParser.parseCode(codeContent);
            case MULTI_FILE -> multiFileCodeParser.parseCode(codeContent);
            default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的生成类型：" + codeGenTypeEnum);
        };
    }
}

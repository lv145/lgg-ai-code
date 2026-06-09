package com.lgg.lggaicode.core;

import com.lgg.lggaicode.ai.model.HtmlCodeResult;
import com.lgg.lggaicode.ai.model.MultiFileCodeResult;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 代码文件解析工具
 */
public class CodeParser {
    private static final Pattern HTML_FILE_PATTERN= Pattern.compile("```html\\s*\\n([\\s\\S]*?)```");
    private static final Pattern CSS_FILE_PATTERN= Pattern.compile("```css\\s*\\n([\\s\\S]*?)```");
    private static final Pattern JS_FILE_PATTERN= Pattern.compile("```(?:javascript|js)\\s*\\n([\\s\\S]*?)```");

    /**
     * 解析HTML代码
     * @param code
     * @return
     */
    public static HtmlCodeResult parseHtmlCode(String code){
        HtmlCodeResult htmlCodeResult = new HtmlCodeResult();
        String htmlCode = extractHtmlCode(code);
        if (htmlCode != null&&!htmlCode.isEmpty()) {
            htmlCodeResult.setHtmlCode(htmlCode.trim());
        }else{
            htmlCodeResult.setHtmlCode(code.trim());
        }

        return htmlCodeResult;
    }

    /**
     * 解析多文件代码
     * @param code
     * @return
     */
    public static MultiFileCodeResult parseMultiFileCode(String code){
        MultiFileCodeResult multiFileCodeResult = new MultiFileCodeResult();
        String htmlCode = extractHtmlCode(code);
        if (htmlCode != null&&!htmlCode.isEmpty()) {
            multiFileCodeResult.setHtmlCode(htmlCode.trim());
        }
        String cssCode = extractCodeByPattern(code, CSS_FILE_PATTERN);
        if (cssCode != null&&!cssCode.isEmpty()) {
            multiFileCodeResult.setCssCode(cssCode.trim());
        }
        String jsCode = extractCodeByPattern(code, JS_FILE_PATTERN);
        if (jsCode != null&&!jsCode.isEmpty()) {
            multiFileCodeResult.setJsCode(jsCode.trim());
        }
        return multiFileCodeResult;
    }

    /**
     * 通过正则表达式从代码文件中提取代码
     * @param content
     * @param pattern
     * @return
     */
    private static String extractCodeByPattern(String content, Pattern pattern) {
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
    /**
     * 从代码文件中提取HTML代码
     * @param content
     * @return
     */
    private static String extractHtmlCode(String content) {
        return extractCodeByPattern(content, HTML_FILE_PATTERN);
    }

}

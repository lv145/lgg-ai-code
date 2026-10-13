package com.lgg.lggaicode.parser;

import com.lgg.lggaicode.ai.model.MultiFileCodeResult;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MultiFileCodeParser implements CodeParser<MultiFileCodeResult> {
    private static final Pattern HTML_FILE_PATTERN= Pattern.compile("```html\\s*\\n([\\s\\S]*?)```");
    private static final Pattern CSS_FILE_PATTERN= Pattern.compile("```css\\s*\\n([\\s\\S]*?)```");
    private static final Pattern JS_FILE_PATTERN= Pattern.compile("```(?:javascript|js)\\s*\\n([\\s\\S]*?)```");

    @Override
    public MultiFileCodeResult parseCode(String codeContent) {
        MultiFileCodeResult result = new MultiFileCodeResult();
        String htmlCode = extractMultiFileCode(codeContent, HTML_FILE_PATTERN);
        String cssCode = extractMultiFileCode(codeContent, CSS_FILE_PATTERN);
        String jsCode = extractMultiFileCode(codeContent, JS_FILE_PATTERN);
        if (htmlCode !=null && !htmlCode.isEmpty()) {
            result.setHtmlCode(htmlCode.trim());
        }
        if (cssCode !=null && !cssCode.isEmpty()) {
            result.setCssCode(cssCode.trim());
        }
        if (jsCode !=null && !jsCode.isEmpty()) {
            result.setJsCode(jsCode.trim());
        }
        return result;
    }

    private  String extractMultiFileCode(String codeContent,Pattern pattern) {
        Matcher matcher = pattern.matcher(codeContent);
        if (matcher.find()){
            return matcher.group(1);
        }
        return null;
    }
}

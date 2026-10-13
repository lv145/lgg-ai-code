package com.lgg.lggaicode.parser;

import com.lgg.lggaicode.ai.model.HtmlCodeResult;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HtmlCodeParser implements CodeParser<HtmlCodeResult> {
    private static final Pattern HTML_FILE_PATTERN= Pattern.compile("```html\\s*\\n([\\s\\S]*?)```");
    @Override
    public HtmlCodeResult parseCode(String codeContent) {
        HtmlCodeResult result = new HtmlCodeResult();
        String htmlCode = extractHtmlCode(codeContent);
        if (htmlCode != null&&!htmlCode.isEmpty()) {
            result.setHtmlCode(htmlCode.trim());
        }else{
            result.setHtmlCode(codeContent.trim());
        }
        return result;
    }

    private String extractHtmlCode(String codeContent){
        Matcher matcher = HTML_FILE_PATTERN.matcher(codeContent);
        if (matcher.find()){
            return matcher.group(1);
        }
        return null;
    }
}

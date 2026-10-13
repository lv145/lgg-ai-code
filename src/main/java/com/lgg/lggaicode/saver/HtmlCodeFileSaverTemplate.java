package com.lgg.lggaicode.saver;

import com.lgg.lggaicode.ai.model.HtmlCodeResult;
import com.lgg.lggaicode.exception.BusinessException;
import com.lgg.lggaicode.exception.ErrorCode;
import com.lgg.lggaicode.model.enums.CodeGenTypeEnum;

/**
 * HTML 代码文件保存模板
 */
public class HtmlCodeFileSaverTemplate extends CodeFileSaverTemplate<HtmlCodeResult> {
    @Override
    protected CodeGenTypeEnum getCodeType() {
        return CodeGenTypeEnum.HTML;
    }

    @Override
    protected void saveFiles(HtmlCodeResult result, String baseDirPath) {
        writeToFile(baseDirPath, "index.html", result.getHtmlCode());
    }
    @Override
    protected void validateInput(HtmlCodeResult result) {
        super.validateInput(result);
        if (result.getHtmlCode().isBlank()){
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "HTML 代码不能为空");
        }
    }
}

package com.lgg.lggaicode.saver;

import com.lgg.lggaicode.ai.model.HtmlCodeResult;
import com.lgg.lggaicode.ai.model.MultiFileCodeResult;
import com.lgg.lggaicode.exception.BusinessException;
import com.lgg.lggaicode.exception.ErrorCode;
import com.lgg.lggaicode.model.enums.CodeGenTypeEnum;

import java.io.File;

public class CodeFileSaverExecutor {

    private static final HtmlCodeFileSaverTemplate htmlCodeFileSaverTemplate = new HtmlCodeFileSaverTemplate();
    private static final MultiFileSaverTemplate multiFileSaverTemplate = new MultiFileSaverTemplate();

    public static File executeSaver(CodeGenTypeEnum codeType, Object codeResult){
        return switch (codeType) {
            case HTML -> htmlCodeFileSaverTemplate.saveCode((HtmlCodeResult) codeResult);
            case MULTI_FILE -> multiFileSaverTemplate.saveCode((MultiFileCodeResult) codeResult);
            default -> throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的代码类型");
        };
    }
}

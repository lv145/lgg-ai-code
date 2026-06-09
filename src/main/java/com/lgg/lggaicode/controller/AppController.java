package com.lgg.lggaicode.controller;

import com.mybatisflex.core.paginate.Page;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.beans.factory.annotation.Autowired;
import com.lgg.lggaicode.model.entity.App;
import com.lgg.lggaicode.service.AppService;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/**
 * 应用 控制层。
 *
 * @author <a href="https://github.com/lv145">LGG</a>
 */
@RestController
@RequestMapping("/app")
public class AppController {

    @Autowired
    private AppService appService;


}

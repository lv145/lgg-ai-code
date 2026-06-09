package com.lgg.lggaicode.service.impl;

import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.lgg.lggaicode.model.entity.App;
import com.lgg.lggaicode.mapper.AppMapper;
import com.lgg.lggaicode.service.AppService;
import org.springframework.stereotype.Service;

/**
 * 应用 服务层实现。
 *
 * @author <a href="https://github.com/lv145">LGG</a>
 */
@Service
public class AppServiceImpl extends ServiceImpl<AppMapper, App>  implements AppService{

}

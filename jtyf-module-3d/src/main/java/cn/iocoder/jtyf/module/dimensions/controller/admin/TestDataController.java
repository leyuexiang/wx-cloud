package cn.iocoder.jtyf.module.dimensions.controller.admin;

import cn.iocoder.jtyf.module.dimensions.entity.TestData;
import cn.iocoder.jtyf.module.dimensions.mapper.TestDataMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/dimensions/test")
@Tag(name = "测试数据表获取接口")
public class TestDataController {

    @Resource
    TestDataMapper testDataMapper;

    @GetMapping("/get-data")
    @Operation(tags = "获取测试全表数据")
    //@PreAuthorize("@ss.hasPermission('dimensions:testData:getData')")
    public List<TestData> getData(){
      return testDataMapper.selectList(new LambdaQueryWrapper<>());
    }

}

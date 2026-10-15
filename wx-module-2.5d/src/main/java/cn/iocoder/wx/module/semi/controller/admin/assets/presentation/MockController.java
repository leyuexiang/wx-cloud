package cn.iocoder.wx.module.semi.controller.admin.assets.presentation;


import cn.iocoder.wx.module.semi.controller.admin.assets.presentation.model.ModelTest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author fc
 * @since 2023-04-30
 */
@RestController
@RequestMapping("/v2/mock")
public class MockController {


    @GetMapping(value="/data")
    public List<ModelTest> mock(HttpServletRequest req, HttpServletResponse res) throws Exception {
        List<ModelTest> list = new ArrayList<>();
        ModelTest s = new ModelTest();
        s.setId("xLineData");
        String str = "周一,周二,周三,周四,周五,周六,周日";
        s.setValue(Arrays.asList(str.split(",")));
        list.add(s);

        ModelTest s1= new ModelTest();
        s1.setId("yLineData");
        String str1 = "10,40,50,30,25,55,35";
        s1.setValue(Arrays.stream(str1.split(",")).map(Integer::parseInt).collect(Collectors.toList()));
        list.add(s1);
        return list;
    }
}

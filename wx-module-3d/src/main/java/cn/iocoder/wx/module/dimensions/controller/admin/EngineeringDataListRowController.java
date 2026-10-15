package cn.iocoder.wx.module.dimensions.controller.admin;

import cn.iocoder.wx.module.dimensions.entity.EngineeringDataListRow;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/dimensions/project-engineering-data-list-row")
@Slf4j
@Tag(name = "场景数据列表-列接口")
public class EngineeringDataListRowController extends BaseCrudController<EngineeringDataListRow, Long> {

/*    public Szl3dEngineeringDataListRowController() {
        setPermissionPrefix("projectEngineeringDataListRow");
    }*/

}
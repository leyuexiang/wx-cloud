package cn.iocoder.jtyf.module.dimensions.service;

import cn.iocoder.jtyf.module.dimensions.entity.FileOnlyInfo;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.LinkedList;

public interface FileOnlyInfoService extends IService<FileOnlyInfo> {

    LinkedList<String> getFileOnlyInfo(String projectId);
}

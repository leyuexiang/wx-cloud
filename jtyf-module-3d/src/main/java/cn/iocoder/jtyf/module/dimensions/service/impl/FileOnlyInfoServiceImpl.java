package cn.iocoder.jtyf.module.dimensions.service.impl;

import cn.iocoder.jtyf.module.dimensions.entity.FileOnlyInfo;
import cn.iocoder.jtyf.module.dimensions.mapper.FileOnlyInfoMapper;
import cn.iocoder.jtyf.module.dimensions.service.FileInfoService;
import cn.iocoder.jtyf.module.dimensions.service.FileOnlyInfoService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.List;

@Service
public class FileOnlyInfoServiceImpl extends ServiceImpl<FileOnlyInfoMapper, FileOnlyInfo> implements FileOnlyInfoService {

    @Resource
    FileInfoService fileInfoService;

    @Override
    public LinkedList<String> getFileOnlyInfo(String projectId) {
         LinkedList<String> fileOnlyInfos = new LinkedList<>();
         List<FileOnlyInfo> list = this.baseMapper.selectList(new LambdaQueryWrapper<FileOnlyInfo>()
                .eq(FileOnlyInfo::getProjectId, projectId));
         for (FileOnlyInfo item:list) {
             String info = fileInfoService.getDownloadPath(item.getParentId(),2) + item.getFileName() + "|"+item.getFileCode();
             fileOnlyInfos.add(info);
         }
         return fileOnlyInfos;
    }}

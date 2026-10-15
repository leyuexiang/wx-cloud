package cn.iocoder.jtyf.module.goview.service;

import cn.iocoder.jtyf.module.goview.model.SysFile;
import com.baomidou.mybatisplus.extension.service.IService;

public interface ISysFileService extends IService<SysFile> {

	
	public SysFile selectByExamplefileName(String filename);
}

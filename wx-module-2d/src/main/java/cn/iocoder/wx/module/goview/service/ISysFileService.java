package cn.iocoder.wx.module.goview.service;

import cn.iocoder.wx.module.goview.model.SysFile;
import com.baomidou.mybatisplus.extension.service.IService;

public interface ISysFileService extends IService<SysFile> {

	
	public SysFile selectByExamplefileName(String filename);
}

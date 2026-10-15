//package cn.iocoder.jtyf.module.dimensions.service.impl;
//
//import cn.iocoder.jtyf.module.dimensions.entity.TimeLineDO;
//import cn.iocoder.jtyf.module.dimensions.mapper.TimeLineMapper;
//import cn.iocoder.jtyf.module.dimensions.service.TimeLineService;
//import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
//import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
//import org.springframework.stereotype.Service;
//
//import java.util.List;
//
//@Service
//public class TimeLineServiceImpl extends ServiceImpl<TimeLineMapper, TimeLineDO> implements TimeLineService {
//
//    @Override
//    public List<TimeLineDO> queryAll() {
//        return list();
//    }
//
//    @Override
//    public TimeLineDO queryByTaskId(String taskId) {
//        return this.baseMapper.selectOne(new LambdaQueryWrapper<TimeLineDO>().eq(TimeLineDO::getTaskId, taskId));
//    }
//}

//package cn.iocoder.jtyf.module.dimensions.config;
//
//
//import cn.hutool.core.collection.CollectionUtil;
//import cn.iocoder.jtyf.module.dimensions.entity.TimeLineDO;
//import cn.iocoder.jtyf.module.dimensions.service.TimeLineService;
//import lombok.AllArgsConstructor;
//import lombok.Getter;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.CommandLineRunner;
//import org.springframework.data.redis.core.StringRedisTemplate;
//import org.springframework.stereotype.Component;
//
//import java.util.List;
//
//@AllArgsConstructor
//@Component
//public class TimeLineDataLoader implements CommandLineRunner {
//
//    @Autowired
//    private TimeLineService timeLineService;
//
//    @Getter
//    private final StringRedisTemplate redisTemplate;
//
//    @Override
//    public void run(String... args) throws Exception {
//
//        List<TimeLineDO> list = timeLineService.list();
//        if (CollectionUtil.isEmpty(list)) return;
//        list.forEach(s -> redisTemplate.opsForValue().set(s.getTaskId(), s.getContent()));
//    }
//}

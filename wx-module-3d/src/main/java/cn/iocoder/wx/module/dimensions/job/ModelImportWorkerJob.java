package cn.iocoder.wx.module.dimensions.job;

import cn.iocoder.wx.module.dimensions.service.ModelImportSourceWorker;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** 用独立线程轮询任务，转换期间由执行器的独立租约线程续租。 */
@Component
@ConditionalOnProperty(prefix="dim3d.model-import",name={"enabled","worker-enabled"},havingValue="true")
public class ModelImportWorkerJob {
    private final ModelImportSourceWorker worker;
    private final java.util.concurrent.ScheduledExecutorService executor=Executors.newSingleThreadScheduledExecutor(task->{
        Thread thread=new Thread(task,"model-import-worker");
        thread.setDaemon(true);
        return thread;
    });

    public ModelImportWorkerJob(ModelImportSourceWorker worker){this.worker=worker;}

    @PostConstruct void start() {
        executor.scheduleWithFixedDelay(()->{
            try {worker.runNext();}
            catch(RuntimeException unavailable) {
                // 保持轮询存活。失败的任务由数据库租约恢复，日志不包含原始文件或连接信息。
                LoggerFactory.getLogger(ModelImportWorkerJob.class).warn("模型导入执行失败，将在下一轮继续检查");
            }
        },2,2,TimeUnit.SECONDS);
    }

    @PreDestroy void close(){executor.shutdownNow();}
}

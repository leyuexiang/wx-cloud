package cn.iocoder.wx.module.semi.common.log;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

@WebListener
public class LoggingInitializerListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        CmnLogger.init();
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
    }
}

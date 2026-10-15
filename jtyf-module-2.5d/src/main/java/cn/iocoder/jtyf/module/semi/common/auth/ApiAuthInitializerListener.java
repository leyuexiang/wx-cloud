package cn.iocoder.jtyf.module.semi.common.auth;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

@WebListener
public class ApiAuthInitializerListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        ApiAuthorization.init();
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
    }
}

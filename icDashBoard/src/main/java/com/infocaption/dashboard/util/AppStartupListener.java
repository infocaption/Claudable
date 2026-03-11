package com.infocaption.dashboard.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

/**
 * Application startup listener. Fires before any servlets are initialized.
 * Loads centralized secrets configuration from WEB-INF/app-secrets.properties.
 */
public class AppStartupListener implements ServletContextListener {

    private static final Logger log = LoggerFactory.getLogger(AppStartupListener.class);

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        log.info("Initializing InfoCaption Dashboard...");
        SecretsConfig.init(sce.getServletContext());
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        log.info("Shutting down InfoCaption Dashboard.");
    }
}

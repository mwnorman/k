package org.ryo.microprofile.bootstrap;

import java.io.IOException;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.eclipse.microprofile.config.spi.ConfigSource;
import org.eclipse.microprofile.config.spi.ConfigSourceProvider;

public class RYOConfigSourceProvider implements ConfigSourceProvider {

    static final String MICRO_PROFILE_DEFAULT_PROPS_LOC ="META-INF/microprofile-config.properties";

    @Override
    public Iterable<ConfigSource> getConfigSources(ClassLoader forClassLoader) {
        ClassLoader cl = forClassLoader;
        if (cl == null) {
            cl = fillInClassLoader();
        }
        //find all META-INF/microprofile-config.properties files, build ConfigSource for each
        List<ConfigSource> configSources = new ArrayList<>();
        Map<String, String> properties = new HashMap<>();
        URL tmpURL = cl.getResource(MICRO_PROFILE_DEFAULT_PROPS_LOC);
        if (tmpURL != null) {
            try {
                Properties tmp = new Properties();
                tmp.load(tmpURL.openStream());
                tmp.forEach((key, value) -> properties.put((String) key, (String) value));
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
        return configSources;
    }

    //handle some pretty 'dirty' classLoader scenarios
    static ClassLoader fillInClassLoader() {
        ClassLoader cl = getContextClassLoader();
        if (cl == null) {
            Class<?> callerClass = getCallerClass();
            if (callerClass != null) {
                cl = callerClass.getClassLoader();
            }
            else {
                cl = ClassLoader.getSystemClassLoader();
            }
        }
        return cl;
    }
    static ClassLoader getContextClassLoader() {
        ClassLoader contextClassLoader = null;
        if (System.getSecurityManager() == null) {
            contextClassLoader = Thread.currentThread().getContextClassLoader();
        }
        else {
            contextClassLoader = AccessController.doPrivileged((PrivilegedAction<ClassLoader>) () ->
                Thread.currentThread().getContextClassLoader());
        }
        return contextClassLoader;
    }
    static Class<?> getCallerClass() {
        Class<?> callerClass = new MySecurityManager().getCallerClass(2);
        return callerClass;
    }
    /**
     * A custom security manager that uses getClassContext() to find who called us
     */
    static class MySecurityManager extends SecurityManager {
        public Class<?> getCallerClass(int callStackDepth) {
            return getClassContext()[callStackDepth];
        }
    }
}

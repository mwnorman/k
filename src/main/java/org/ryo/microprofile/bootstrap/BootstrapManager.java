package org.ryo.microprofile.bootstrap;

import static org.ryo.microprofile.bootstrap.BootstrapManager.BootstrapManagerHelper.DOT_CLASS;
import static org.ryo.microprofile.bootstrap.BootstrapManager.BootstrapManagerHelper.DOT_JAR;
import static org.ryo.microprofile.bootstrap.BootstrapManager.BootstrapManagerHelper.DOT_ZIP;
import static org.ryo.microprofile.bootstrap.BootstrapManager.BootstrapManagerHelper.JAVA_CLASS_PATH;
import static org.ryo.microprofile.bootstrap.BootstrapManager.BootstrapManagerHelper.PACKAGE_INFO_CLASS;
import static org.ryo.microprofile.bootstrap.BootstrapManager.BootstrapManagerHelper.SLASH_REGEX;

import java.io.File;
import java.io.FilenameFilter;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.function.BiFunction;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import javax.annotation.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BootstrapManager {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    static final FilenameFilter PACKAGE_INFO_FILTER = (dir, name) -> name.endsWith(PACKAGE_INFO_CLASS);
    static final BiFunction<File, String, File> fileConstructorWithParentFileAndName = File::new;
    static final FilenameFilter DIRECTORIES_FILTER = (dir, name) -> fileConstructorWithParentFileAndName.apply(dir, name).isDirectory();

    public static BootstrapManager getBootstrapManager() {
        return BootstrapManagerHelper.getBootstrapManager();
    }
    //is this necessary?
    public static void setBootstrapManager(BootstrapManager bootstrapManager) {
        BootstrapManagerHelper.setBootstrapManager(bootstrapManager);
    }
    //non-public constructor: use above static getBootstrapManager; however allows for possibility sub-class may wish to override
    protected BootstrapManager() {
        try {
            scanForPackages();
        }
        catch (Exception e) {
            //this should never happen!
            logger.error("unable to retrieve {} property", JAVA_CLASS_PATH, e.getCause());
        }
    }

    protected List<PackageInfo> foundPackages = new ArrayList<>();
    protected Map<String, ResourceInfo> resources = new HashMap<>();

    public static void main(String... args) {
        logger.info("starting main");
    }

    protected void scanForPackages() throws BootstrapManagerHelperException {
        String javaClassPath = null;
        try {
            javaClassPath = System.getProperty(JAVA_CLASS_PATH);
        }
        catch (Exception e) {
            //no point in trying after this
            throw new BootstrapManagerHelperException(e);
        }

        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = ClassLoader.getSystemClassLoader();
        }
        logger.trace("scanning classpath for packages ...");
        StringTokenizer tokenizer = new StringTokenizer(javaClassPath, File.pathSeparator);
        while (tokenizer.hasMoreTokens()) {
            String possibleStartingDirectoryPath = tokenizer.nextToken();
            File possibleStartingDir = new File(possibleStartingDirectoryPath);
            if (possibleStartingDir.isDirectory()) {
                File startingDir = possibleStartingDir;
                List<File> packageInfoFiles = new ArrayList<>();
                scanDirForMatchingFiles(packageInfoFiles, startingDir, PACKAGE_INFO_FILTER);
                if (packageInfoFiles.size() > 0) {
                    for (File packageInfoFile : packageInfoFiles) {
                        PackageInfo pi = new PackageInfo();
                        pi.rootPath = startingDir;
                        pi.packageInfoClass = packageInfoFile;
                        foundPackages.add(pi);
                    }
                }
            }
            else if (possibleStartingDir.isFile()) {
                String possibleStartingDirPath = possibleStartingDir.getPath();
                boolean dotJarorZip = possibleStartingDirPath.endsWith(DOT_JAR) || possibleStartingDirPath.endsWith(DOT_ZIP);
                if (dotJarorZip) {
                    List<String> packageInfoClassNames = new ArrayList<>();
                    scanJarForMatchingFiles(packageInfoClassNames, possibleStartingDir);
                    if (packageInfoClassNames.size() > 0) {
                        for (String packageInfoClassName : packageInfoClassNames) {
                            try {
                                PackageInfo pi = new PackageInfo();
                                Package pkg = cl.loadClass(packageInfoClassName).getPackage();
                                pi.pkg = pkg;
                                foundPackages.add(pi);
                            }
                            catch (Exception e) {
                                logger.error("problem loading {}", packageInfoClassName, e);
                            }
                        }
                    }
                }
            }
        }
        if (foundPackages.size() > 0) {
            for (PackageInfo packageInfo : foundPackages) {
                if (packageInfo.pkg == null) {
                    String rootPath = packageInfo.rootPath.getPath();
                    String packageInfoPath = packageInfo.packageInfoClass.getPath();
                    String className = packageInfoPath.substring(rootPath.length()+1,
                        packageInfoPath.length() - DOT_CLASS.length()).replaceAll(SLASH_REGEX, ".");
                    try {
                        Package pkg = cl.loadClass(className).getPackage();
                        packageInfo.pkg = pkg;
                        logger.trace("found package-info in package: {}", pkg.getName());
                    }
                    catch (Exception e) {
                        logger.error("problem loading {}", className, e);
                    }
                }
            }
        }
    }

    static void scanDirForMatchingFiles(List<File> matchingFiles, File currentDir, FilenameFilter filter) {
        if (currentDir.isDirectory()) {
            File[] childFiles = currentDir.listFiles(filter);
            for (int i = 0; i < childFiles.length; i++) {
                logger.trace("scan found package-info for class {}", childFiles[i].toString());
                matchingFiles.add(childFiles[i]);
            }
            String[] childDirs = currentDir.list(DIRECTORIES_FILTER);
            for (int i = 0; i < childDirs.length; i++) {
                scanDirForMatchingFiles(matchingFiles, new File(currentDir, childDirs[i]), filter);
            }
        }
    }

    static void scanJarForMatchingFiles(List<String> matchingFiles, File jarfile) {
        try (JarFile jar = new JarFile(jarfile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().toString();
                if (name.endsWith(PACKAGE_INFO_CLASS)) {
                    String className = name.substring(0, name.length() - DOT_CLASS.length()).replaceAll(SLASH_REGEX, ".");
                    logger.trace("scan found class {} in jar {}", className, jarfile);
                    matchingFiles.add(className);
                }
            }
        }
        catch (Exception e) {
            logger.error("problem scanning jar for matching files", e);
        }
    }

    //someday (perhaps Java 11?) there will be tuples or value-classes or structs
    static class PackageInfo {
        File rootPath;
        File packageInfoClass;
        Package pkg;
    }
    static class ResourceInfo {
        Class<?> resourceType;
        Object resource;
    }
    static class ResourceAnnotation {
        Field field;
        Resource resourceAnnotation;
    }
    static class BootstrapManagerHelperException extends RuntimeException {
        public BootstrapManagerHelperException(Throwable cause) {
            super(cause);
        }
    }
    static class BootstrapManagerHelper {
        public static final String DOT_CLASS = ".class";
        public static final String JAVA_CLASS_PATH = "java" + DOT_CLASS + ".path";
        public static final String DOT_JAR = ".jar";
        public static final String DOT_ZIP = ".zip";
        public static final String PACKAGE_INFO_CLASS = "package-info" + DOT_CLASS;
        public static final String SLASH_REGEX = "[\\\\,/]";
        static BootstrapManager BOOTSTRAP_MANAGER_SINGLETON = new BootstrapManager();
        static BootstrapManager getBootstrapManager() {
            return BOOTSTRAP_MANAGER_SINGLETON;
        }
        static void setBootstrapManager(BootstrapManager bootstrapManager) {
            BOOTSTRAP_MANAGER_SINGLETON = bootstrapManager;
        }
    }
}
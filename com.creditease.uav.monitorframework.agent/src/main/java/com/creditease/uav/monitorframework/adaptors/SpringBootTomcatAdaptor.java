/*-
 * <<
 * UAVStack
 * ==
 * Copyright (C) 2016 - 2017 UAVStack
 * ==
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * >>
 */

package com.creditease.uav.monitorframework.adaptors;

import com.creditease.agent.helpers.ReflectionHelper;

import javassist.ClassPool;
import javassist.CtMethod;

public class SpringBootTomcatAdaptor extends AbstractAdaptor {

    private String springBootVersion = "";

    private void getSpringBootVersion(ClassLoader clsLoader, String className) {

        if (!"".equals(springBootVersion) || clsLoader == null) {
            return;
        }

        /**
         * NOTE:this class is used to detect springboot version. it exists in all versions of springboot, and we
         * probably will never inject this class.
         */
        String versionDetectClassName = "org.springframework.boot.Banner";

        if (versionDetectClassName.equals(className)) {
            return;
        }

        Class<?> springBootVersionDetectClass = ReflectionHelper.tryLoadClass(versionDetectClassName, clsLoader);

        if (springBootVersionDetectClass == null) {
            return;
        }

        Package pkg = springBootVersionDetectClass.getPackage();
        String version = (pkg != null) ? pkg.getImplementationVersion() : null;
        springBootVersion = (version != null) ? version : "UnKnownVersion";
        System.out.println("MOF.ApplicationVersion=SpringBoot " + springBootVersion);
    }

    @Override
    public byte[] onStartup(ClassLoader clsLoader, String uavMofRoot, String className) {

        System.out.println("MOF.ApplicationContainer=SpringBoot.Tomcat");

        if (pool == null) {
            pool = ClassPool.getDefault();
        }

        final AbstractAdaptor aa = this;

        final String mofRoot = uavMofRoot;

        if ("org.springframework.boot.loader.Launcher".equals(className)) {
            return this.inject("org.springframework.boot.loader.Launcher",
                    new String[] { "com.creditease.uav.monitorframework.agent.interceptor" }, new AdaptorProcessor() {

                        @Override
                        public void process(CtMethod m) throws Exception {

                            aa.addLocalVar(m, "mObj",
                                    "com.creditease.uav.monitorframework.agent.interceptor.SpringBootTomcatIT");
                            m.insertAfter("{mObj=new SpringBootTomcatIT(\"" + mofRoot + "\"); mObj.installMOF($_);}");
                        }

                        @Override
                        public String getMethodName() {

                            return "createClassLoader";
                        }

                    });
        }
        else if ("org.springframework.boot.loader.PropertiesLauncher".equals(className)) {
            return this.inject("org.springframework.boot.loader.PropertiesLauncher",
                    new String[] { "com.creditease.uav.monitorframework.agent.interceptor" }, new AdaptorProcessor() {

                        @Override
                        public void process(CtMethod m) throws Exception {

                            aa.addLocalVar(m, "mObj",
                                    "com.creditease.uav.monitorframework.agent.interceptor.SpringBootTomcatIT");
                            m.insertAfter("{mObj=new SpringBootTomcatIT(\"" + mofRoot + "\"); mObj.installMOF($_);}");
                        }

                        @Override
                        public String getMethodName() {

                            return "createClassLoader";
                        }

                    });
        }
        else if ("org.springframework.boot.SpringApplication".equals(className)) {
            return this.inject("org.springframework.boot.SpringApplication",
                    new String[] { "com.creditease.uav.monitorframework.agent.interceptor" }, new AdaptorProcessor() {

                        @Override
                        public void process(CtMethod m) throws Exception {

                            aa.addLocalVar(m, "mObj",
                                    "com.creditease.uav.monitorframework.agent.interceptor.SpringBootTomcatIT");
                            m.insertBefore("{mObj=new SpringBootTomcatIT(\"" + mofRoot
                                    + "\"); mObj.installMOF(getClassLoader());}");
                        }

                        @Override
                        public String getMethodName() {

                            return "createApplicationContext";
                        }

                    });
        }
        return null;
    }

    @Override
    public byte[] onLoadClass(ClassLoader clsLoader, final String uavMofRoot, String className) {

        getSpringBootVersion(clsLoader, className);

        this.addClassPath(clsLoader);

        final AbstractAdaptor aa = this;

        // log4j劫持
        if (className.equals("org.apache.log4j.helpers.QuietWriter")) {
            try {
                String logJarPath = uavMofRoot + "/com.creditease.uav.appfrk/com.creditease.uav.loghook-1.0.jar";
                aa.installJar(clsLoader, logJarPath, true);
                // 兼容在ide环境下启动
                String mofJarPath = uavMofRoot + "/com.creditease.uav/com.creditease.uav.monitorframework-1.0.jar";
                aa.installJar(clsLoader, mofJarPath, true);
                aa.defineField("uavLogHook", "com.creditease.uav.log.hook.interceptors.LogIT",
                        "org.apache.log4j.helpers.QuietWriter", "new LogIT()");
                aa.defineField("uavLogHookLineSep", "java.lang.String", "org.apache.log4j.helpers.QuietWriter",
                        "System.getProperty(\"line.separator\")");
            }
            catch (Exception e) {
                System.out.println("MOF.Interceptor[\" springboot \"] Install MonitorFramework Jars FAIL.");
                e.printStackTrace();
            }
            return this.inject(className, new String[] { "com.creditease.uav.log.hook.interceptors" },
                    new AdaptorProcessor() {

                        @Override
                        public void process(CtMethod m) throws Exception {

                            m.insertBefore("{if(!$1.equals(uavLogHookLineSep)){$1=uavLogHook.formatLog($1);}}");
                        }

                        @Override
                        public String getMethodName() {

                            return "write";
                        }

                    });
        }

        // for log4j RollingFileAppender
        else if (className.equals("org.apache.log4j.helpers.CountingQuietWriter")) {
            try {
                String logJarPath = uavMofRoot + "/com.creditease.uav.appfrk/com.creditease.uav.loghook-1.0.jar";
                aa.installJar(clsLoader, logJarPath, true);
                // 兼容在ide环境下启动
                String mofJarPath = uavMofRoot + "/com.creditease.uav/com.creditease.uav.monitorframework-1.0.jar";
                aa.installJar(clsLoader, mofJarPath, true);
                aa.defineField("uavLogHook", "com.creditease.uav.log.hook.interceptors.LogIT",
                        "org.apache.log4j.helpers.CountingQuietWriter", "new LogIT()");
                aa.defineField("uavLogHookLineSep", "java.lang.String", "org.apache.log4j.helpers.CountingQuietWriter",
                        "System.getProperty(\"line.separator\")");
            }
            catch (Exception e) {
                System.out.println("MOF.Interceptor[\" springboot \"] Install MonitorFramework Jars FAIL.");
                e.printStackTrace();
            }
            return this.inject(className, new String[] { "com.creditease.uav.log.hook.interceptors" },
                    new AdaptorProcessor() {

                        @Override
                        public void process(CtMethod m) throws Exception {

                            m.insertBefore("{if(!$1.equals(uavLogHookLineSep)){$1=uavLogHook.formatLog($1);}}");
                        }

                        @Override
                        public String getMethodName() {

                            return "write";
                        }

                    });
        }

        // 进行log4j2的劫持
        else if (className.equals("org.apache.logging.log4j.core.layout.PatternLayout")) {
            try {
                String logJarPath = uavMofRoot + "/com.creditease.uav.appfrk/com.creditease.uav.loghook-1.0.jar";
                aa.installJar(clsLoader, logJarPath, true);
                // 兼容在ide环境下启动
                String mofJarPath = uavMofRoot + "/com.creditease.uav/com.creditease.uav.monitorframework-1.0.jar";
                aa.installJar(clsLoader, mofJarPath, true);
                aa.defineField("uavLogHook", "com.creditease.uav.log.hook.interceptors.LogIT",
                        "org.apache.logging.log4j.core.layout.PatternLayout", "new LogIT()");
            }
            catch (Exception e) {
                System.out.println("MOF.Interceptor[\" springboot \"] Install MonitorFramework Jars FAIL.");
                e.printStackTrace();
            }
            return this.inject(className,
                    new String[] { "com.creditease.uav.log.hook", "com.creditease.uav.log.hook.interceptors" },
                    new AdaptorProcessor() {

                        @Override
                        public void process(CtMethod m) throws Exception {

                            m.insertAfter("{$_=uavLogHook.formatLog($_);}");
                        }

                        @Override
                        public String getMethodName() {

                            return "toText";
                        }

                    }, new AdaptorProcessor() {

                        @Override
                        public void process(CtMethod m) throws Exception {

                            if ("String".equals(m.getReturnType().getSimpleName())) {
                                m.insertAfter("{$_=uavLogHook.formatLog($_);}");
                            }
                        }

                        @Override
                        public String getMethodName() {

                            return "toSerializable";
                        }

                    });
        }

        // 进行logback的劫持
        else if (className.equals("ch.qos.logback.core.encoder.LayoutWrappingEncoder")) {
            try {
                String logJarPath = uavMofRoot + "/com.creditease.uav.appfrk/com.creditease.uav.loghook-1.0.jar";
                aa.installJar(clsLoader, logJarPath, true);
                // 兼容在ide环境下启动
                String mofJarPath = uavMofRoot + "/com.creditease.uav/com.creditease.uav.monitorframework-1.0.jar";
                aa.installJar(clsLoader, mofJarPath, true);
                aa.defineField("uavLogHook", "com.creditease.uav.log.hook.interceptors.LogIT",
                        "ch.qos.logback.core.encoder.LayoutWrappingEncoder", "new LogIT()");
            }
            catch (Exception e) {
                System.out.println("MOF.Interceptor[\" springboot \"] Install MonitorFramework Jars FAIL.");
                e.printStackTrace();
            }
            return this.inject(className,
                    new String[] { "com.creditease.uav.log.hook", "com.creditease.uav.log.hook.interceptors" },
                    new AdaptorProcessor() {

                        @Override
                        public void process(CtMethod m) throws Exception {

                            m.insertBefore("{$1=uavLogHook.formatLog($1);}");
                        }

                        @Override
                        public String getMethodName() {

                            return "convertToBytes";
                        }

                    });
        }

        else if (className.equals("org.springframework.context.support.AbstractApplicationContext"))

        {
            return this.inject(className, new String[] { "com.creditease.tomcat.plus.interceptor" },
                    new AdaptorProcessor() {

                        /**
                         * we need startServer before ApplicationContext's refresh cause some hook operation could
                         * happen when refresh. the hook is done after startServer before refresh, and profiling will be
                         * done after refresh
                         */
                        @Override
                        public void process(CtMethod m) throws Exception {

                            aa.addLocalVar(m, "mObj", "com.creditease.tomcat.plus.interceptor.SpringBootTomcatPlusIT");
                            String contextPathKey = springBootVersion.startsWith("2") ? "server.servlet.context-path"
                                    : "server.context-path";
                            String sb = "{mObj = new SpringBootTomcatPlusIT();"
                                    + "mObj.startServer(this.getEnvironment().getProperty(\"server.port\"),this.getEnvironment().getProperty(\"spring.application.name\"),this);"
                                    + "mObj.onSpringBeanRegist(new Object[]{this,this.getEnvironment().getProperty(\""
                                    + contextPathKey + "\")});}";
                            m.insertBefore(sb);
                            m.insertAfter("{mObj.onSpringFinishRefresh(this);}");

                        }

                        @Override
                        public String getMethodName() {

                            return "refresh";
                        }

                    });
        }

        else if (className.equals("org.apache.catalina.core.StandardEngineValve")) {
            return this.inject(className, new String[] { "com.creditease.tomcat.plus.interceptor" },
                    new AdaptorProcessor() {

                        @Override
                        public void process(CtMethod m) throws Exception {

                            aa.addLocalVar(m, "mObj", "com.creditease.tomcat.plus.interceptor.SpringBootTomcatPlusIT");
                            m.insertBefore(
                                    "{mObj=new SpringBootTomcatPlusIT();mObj.onServiceStart(new Object[]{this});}");
                            m.insertAfter("{mObj.onServiceEnd(new Object[]{$1,$2});}");
                        }

                        @Override
                        public String getMethodName() {

                            return "invoke";
                        }

                    });
        }

        else if (className.equals("org.apache.catalina.core.StandardContext")) {

            return this.inject(className,
                    new String[] { "com.creditease.tomcat.plus.interceptor", "org.apache.catalina.deploy",
                            "org.apache.catalina.core", "org.apache.tomcat.util.descriptor.web" },
                    new AdaptorProcessor[] { new AdaptorProcessor() {

                        @Override
                        public void process(CtMethod m) throws Exception {

                            aa.addLocalVar(m, "mObj", "com.creditease.tomcat.plus.interceptor.SpringBootTomcatPlusIT");
                            m.insertAfter(
                                    "{mObj=new SpringBootTomcatPlusIT();mObj.onAppStarting(new Object[]{this});                                    }");
                        }

                        @Override
                        public String getMethodName() {

                            return "mergeParameters";
                        }

                    }, new AdaptorProcessor() {

                        @Override
                        public void process(CtMethod m) throws Exception {

                            aa.addLocalVar(m, "mObj", "com.creditease.tomcat.plus.interceptor.SpringBootTomcatPlusIT");
                            m.insertBefore("{mObj=new SpringBootTomcatPlusIT();mObj.onAppInit(new Object[]{this});}");
                            String sb = "{mObj.onAppStart(new Object[]{this});" + "FilterDef fd=new FilterDef();"
                                    + "fd.setFilterClass(\"com.creditease.monitor.jee.filters.GlobalFilter\");"
                                    + "fd.setDisplayName(\"UAV_Global_Filter\");"
                                    + "fd.setFilterName(\"UAV_Global_Filter\");"
                                    + "ApplicationFilterConfig filterConfig = new ApplicationFilterConfig(this, fd);"
                                    + "this.filterConfigs.put(\"UAV_Global_Filter\", filterConfig);"
                                    + "FilterMap filterMap=new FilterMap();"
                                    + "filterMap.setFilterName(\"UAV_Global_Filter\");"
                                    + "filterMap.addURLPattern(\"/*\");" + "this.filterMaps.addBefore(filterMap);"
                                    + "}";

                            m.insertAfter(sb);
                        }

                        @Override
                        public String getMethodName() {

                            return "startInternal";
                        }

                    }, new AdaptorProcessor() {

                        @Override
                        public void process(CtMethod m) throws Exception {

                            aa.addLocalVar(m, "mObj", "com.creditease.tomcat.plus.interceptor.SpringBootTomcatPlusIT");
                            m.insertAfter("{mObj=new SpringBootTomcatPlusIT();mObj.onAppStop(new Object[]{this});}");
                        }

                        @Override
                        public String getMethodName() {

                            return "stopInternal";
                        }

                    } });
        }
        else if (className.equals("org.apache.catalina.core.ApplicationContext")) {

            return this.inject(className, new String[] { "com.creditease.tomcat.plus.interceptor" },
                    new AdaptorProcessor[] { new AdaptorProcessor() {

                        @Override
                        public void process(CtMethod m) throws Exception {

                            aa.addLocalVar(m, "mObj", "com.creditease.tomcat.plus.interceptor.SpringBootTomcatPlusIT");
                            m.insertAfter(
                                    "{mObj=new SpringBootTomcatPlusIT();return mObj.onServletRegist(new Object[]{$_});}");
                        }

                        @Override
                        public String getMethodName() {

                            return "getFacade";
                        }

                    } });
        }
        else if (className.equals("org.apache.catalina.core.StandardWrapper")) {
            return this.inject(className, new String[] { "com.creditease.tomcat.plus.interceptor" },
                    new AdaptorProcessor[] { new AdaptorProcessor() {

                        @Override
                        public void process(CtMethod m) throws Exception {

                            aa.addLocalVar(m, "mObj", "com.creditease.tomcat.plus.interceptor.SpringBootTomcatPlusIT");
                            m.insertAfter(
                                    "{mObj=new SpringBootTomcatPlusIT();mObj.onServletStart(new Object[]{this,$_});}");
                        }

                        @Override
                        public String getMethodName() {

                            return "loadServlet";
                        }

                    }, new AdaptorProcessor() {

                        @Override
                        public void process(CtMethod m) throws Exception {

                            aa.addLocalVar(m, "mObj", "com.creditease.tomcat.plus.interceptor.SpringBootTomcatPlusIT");
                            m.insertBefore(
                                    "{mObj=new SpringBootTomcatPlusIT();mObj.onServletStop(new Object[]{this,this.instance});}");
                        }

                        @Override
                        public String getMethodName() {

                            return "unload";
                        }

                    } });
        }
        else if (
        // after Tomcat 8
        className.equals("org.apache.naming.factory.FactoryBase") ||
        // before Tomcat 7
                className.equals("org.apache.naming.factory.ResourceFactory")) {
            return this.inject(className, new String[] { "com.creditease.tomcat.plus.interceptor" },
                    new AdaptorProcessor[] { new AdaptorProcessor() {

                        @Override
                        public String getMethodName() {

                            return "getObjectInstance";
                        }

                        @Override
                        public void process(CtMethod m) throws Exception {

                            aa.addLocalVar(m, "mObj", "com.creditease.tomcat.plus.interceptor.SpringBootTomcatPlusIT");
                            m.insertBefore(
                                    "{mObj=new SpringBootTomcatPlusIT();mObj.onResourceInit(new Object[]{$1});}");
                            m.insertAfter("{$_=mObj.onResourceCreate(new Object[]{$_,$1});}");
                        }

                    } });
        }
        // onDeployUAVApp
        else if (className.equals("org.apache.catalina.startup.Tomcat")) {

            return this.inject(className, new String[] { "com.creditease.tomcat.plus.interceptor" },
                    new AdaptorProcessor[] { new AdaptorProcessor() {

                        @Override
                        public String getMethodName() {

                            return "start";
                        }

                        @Override
                        public void process(CtMethod m) throws Exception {

                            aa.addLocalVar(m, "mObj", "com.creditease.tomcat.plus.interceptor.SpringBootTomcatPlusIT");
                            m.insertBefore("{mObj=new SpringBootTomcatPlusIT();mObj.onDeployUAVApp(new Object[]{this,\""
                                    + uavMofRoot + "\"});}");
                        }

                    } });
        }

        return null;
    }

}

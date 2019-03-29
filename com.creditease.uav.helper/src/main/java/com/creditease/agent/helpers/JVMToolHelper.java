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

package com.creditease.agent.helpers;

import java.io.File;
import java.io.IOException;
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServerConnection;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.openmbean.CompositeData;

import com.creditease.agent.helpers.jvmtool.JVMAgentInfo;
import com.creditease.agent.helpers.jvmtool.JVMPropertyFilter;

public class JVMToolHelper {

    public static final String osname = System.getProperty("os.name").toLowerCase();
    public static final String username = System.getProperty("user.name");
    public static final String JMX_CONNECTOR_ADDRESS = "com.sun.management.jmxremote.localConnectorAddress";

    private static ClassLoader JVMToolClassloader = null;
    private static final Object lock = new Object();
    private static Class<?> virtualMachine;
    private static Class<?> virtualMachineDescriptor;
    private static Method method_VMList;
    private static Method method_AttachToVM;
    private static Method method_DetachFromVM;
    private static Method method_GetAgentProperties;
    private static Method method_VMId;
    private static Method method_GetSystemProperties;
    private static Method method_LoadAgent;
    private static Method method_StartLocalManagementAgent;

    private static Class<?> hostIdentifierClass;
    private static Class<?> monitoredVmClass;
    private static Class<?> vmIdentifierClass;

    private JVMToolHelper() {

    }

    public static String getJVMVendor() {

        return System.getProperty("java.vm.specification.vendor");
    }

    public static boolean isOracleJVM() {

        return "Sun Microsystems Inc.".equals(getJVMVendor()) || getJVMVendor().startsWith("Oracle");
    }

    public static boolean isVMAlive(String procId) {

        if (procId == null || "".equals(procId)) {
            return false;
        }

        initJVMToolJarClassLoader();

        try {
            Object vm = method_AttachToVM.invoke(null, procId);
            if (vm != null) {

                method_DetachFromVM.invoke(vm);
                return true;
            }
        }
        catch (Exception e) {
            // ignore
        }

        return false;
    }

    public static JVMAgentInfo getLocalJvmInfo(String procId, boolean needLocalAttachSupport) {

        if (procId == null || "".equals(procId)) {
            return null;
        }

        initJVMToolJarClassLoader();

        try {
            Object vm = method_AttachToVM.invoke(null, procId);
            if (vm != null) {

                if (needLocalAttachSupport) {
                    startVMAgent(vm);
                }

                Properties jvmProperties = (Properties) method_GetAgentProperties.invoke(vm, (Object[]) null);
                // system properties
                Properties systemProperties = (Properties) method_GetSystemProperties.invoke(vm, (Object[]) null);

                method_DetachFromVM.invoke(vm);

                return new JVMAgentInfo(procId, jvmProperties, systemProperties);
            }
        }
        catch (Exception e) {
            // ignore
        }

        return null;
    }

    public static List<JVMAgentInfo> getAllLocalJvmInfo(JVMPropertyFilter filter, boolean needLocalAttachSupport,
            Set<String> skipLocalAttachSupport) {

        if (!isOracleJVM()) {
            return Collections.emptyList();
        }

        List<JVMAgentInfo> jvmPropertiesList = new ArrayList<JVMAgentInfo>();

        try {
            initJVMToolJarClassLoader();

            @SuppressWarnings("rawtypes")
            List allVMs = (List) method_VMList.invoke(null, (Object[]) null);

            getAllVMsInfo(filter, needLocalAttachSupport, jvmPropertiesList, allVMs, skipLocalAttachSupport);

        }
        catch (Exception e) {
            // ignore
        }

        return jvmPropertiesList;
    }

    /**
     * @param filter
     * @param needLocalAttachSupport
     * @param jvmPropertiesList
     * @param allVMs
     */
    @SuppressWarnings("rawtypes")
    private static void getAllVMsInfo(JVMPropertyFilter filter, boolean needLocalAttachSupport,
            List<JVMAgentInfo> jvmPropertiesList, List allVMs, Set<String> skipLocalAttachSupport) {

        for (Object vmInstance : allVMs) {

            /**
             * now we only support 64bit JVM, in case the 32bit JVM is attached with exception
             */
            try {
                String id = (String) method_VMId.invoke(vmInstance, (Object[]) null);
				
                //if the jvm is not started by the same user as MA, do not attach it (just in case of linux)             
                if(isLinux() && !username.equals(Files.getOwner(Paths.get(("/proc/"+id))).getName())) {
                    continue;
                }
				
                Object vm = method_AttachToVM.invoke(null, id);

                if (vm == null) {
                    continue;
                }

                // agent properties
                Properties jvmProperties = (Properties) method_GetAgentProperties.invoke(vm, (Object[]) null);

                // system properties
                Properties systemProperties = (Properties) method_GetSystemProperties.invoke(vm, (Object[]) null);

                if (jvmProperties != null) {

                    if (filter != null && (!filter.isMatchAgentProperties(jvmProperties)
                            || !filter.isMatchSystemProperties(systemProperties))) {
                        continue;
                    }

                    boolean isNeedLocalAttachSupport = needLocalAttachSupport;
                    if (skipLocalAttachSupport != null) {
                        isNeedLocalAttachSupport = (skipLocalAttachSupport.contains(id) == true) ? false
                                : needLocalAttachSupport;
                    }

                    jvmProperties = fillJVMProperties(isNeedLocalAttachSupport, vm, jvmProperties);

                    jvmPropertiesList.add(new JVMAgentInfo(id, jvmProperties, systemProperties));
                }

                method_DetachFromVM.invoke(vm);
            }
            catch (Exception e) {
                // ignore
                continue;
            }
        }
    }

    /**
     * @param needLocalAttachSupport
     * @param vm
     * @param jvmProperties
     * @return
     * @throws IllegalAccessException
     * @throws IllegalArgumentException
     * @throws InvocationTargetException
     */
    private static Properties fillJVMProperties(boolean needLocalAttachSupport, Object vm, Properties jvmProperties)
            throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {

        if (needLocalAttachSupport) {
            startVMAgent(vm);
            jvmProperties = (Properties) method_GetAgentProperties.invoke(vm, (Object[]) null);
        }

        return jvmProperties;
    }

    private static void startVMAgent(Object vm) {

        try {
            Properties jvmProperties = (Properties) method_GetAgentProperties.invoke(vm, (Object[]) null);
            if (jvmProperties.get(JMX_CONNECTOR_ADDRESS) == null) {

                Properties systemProperties = (Properties) method_GetSystemProperties.invoke(vm, (Object[]) null);
                String jversion = (String) systemProperties.get("java.version");
                if(null != jversion && jversion.startsWith("9.")){
                    
                    if(null == method_StartLocalManagementAgent ) {
                        return;
                    }
                    method_StartLocalManagementAgent.invoke(vm, (Object[]) null);
                }else {
                    String agent = systemProperties.getProperty("java.home") + File.separator + "lib" + File.separator
                            + "management-agent.jar";
                    method_LoadAgent.invoke(vm, new Object[] { agent });
                }
            }
        }
        catch (Exception e) {
            // ignore
        }
    }

    private static void initJVMToolJarClassLoader() {

        String javaHome = System.getProperty("java.home");
        String javaVersion = System.getProperty("java.version");

        String tools = javaHome + File.separator + ".." + File.separator + "lib" + File.separator + "tools.jar";
        
        if(!IOHelper.exists(tools)) {
            tools = javaHome + File.separator + "lib" + File.separator + "tools.jar";
        }

        if (JVMToolClassloader == null) {
            synchronized (lock) {
                if (JVMToolClassloader == null) {
                    try {
                        JVMToolClassloader = new URLClassLoader(new URL[] { new File(tools).toURI().toURL() });

                        // virtual machine
                        virtualMachine = JVMToolClassloader.loadClass("com.sun.tools.attach.VirtualMachine");
                        virtualMachineDescriptor = JVMToolClassloader
                                .loadClass("com.sun.tools.attach.VirtualMachineDescriptor");

                        method_VMList = virtualMachine.getMethod("list", (Class[]) null);
                        method_AttachToVM = virtualMachine.getMethod("attach", String.class);
                        method_DetachFromVM = virtualMachine.getMethod("detach");
                        method_GetAgentProperties = virtualMachine.getMethod("getAgentProperties", (Class[]) null);
                        method_VMId = virtualMachineDescriptor.getMethod("id", (Class[]) null);
                        method_GetSystemProperties = virtualMachine.getMethod("getSystemProperties", (Class[]) null);
                        method_LoadAgent = virtualMachine.getMethod("loadAgent", new Class[] { String.class });
                        if(null != javaVersion && javaVersion.startsWith("9.")) {
                            method_StartLocalManagementAgent = virtualMachine.getMethod("startLocalManagementAgent", (Class[]) null);                        
                        }
                        
                        // java process
                        hostIdentifierClass = JVMToolClassloader.loadClass("sun.jvmstat.monitor.HostIdentifier");
                        monitoredVmClass = JVMToolClassloader.loadClass("sun.jvmstat.monitor.MonitoredVm");
                        vmIdentifierClass = JVMToolClassloader.loadClass("sun.jvmstat.monitor.VmIdentifier");

                    }
                    catch (Exception e) {
                        // ignore
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    public static List<Map<String, String>> getAllJVMProcesses(String host) {

        initJVMToolJarClassLoader();

        // HostIdentifier localHostIdentifier = new HostIdentifier(host);
        Object localHostIdentifier = ReflectionHelper.newInstance("sun.jvmstat.monitor.HostIdentifier",
                new Class[] { String.class }, new Object[] { host }, JVMToolClassloader);

        // localObject1 = MonitoredHost.getMonitoredHost(localHostIdentifier);
        Object localObject1 = ReflectionHelper.invokeStatic("sun.jvmstat.monitor.MonitoredHost", "getMonitoredHost",
                new Class<?>[] { hostIdentifierClass }, new Object[] { localHostIdentifier }, JVMToolClassloader);

        // Set<Integer> localSet = ((MonitoredHost)localObject1).activeVms();
        @SuppressWarnings("unchecked")
        Set<Integer> localSet = (Set<Integer>) ReflectionHelper.invoke("sun.jvmstat.monitor.MonitoredHost", localObject1,
                "activeVms", new Class[] {}, new Object[] {}, JVMToolClassloader);

        if (localSet == null) {
            return Collections.emptyList();
        }

        List<Map<String, String>> vms = new ArrayList<Map<String, String>>();

        for (Integer localIterator : localSet) {

            int pid = localIterator.intValue();

            String str1 = "//" + pid + "?mode=r";

            try {

                // VmIdentifier localVmIdentifier = new VmIdentifier(str1);
                Object localVmIdentifier = ReflectionHelper.newInstance("sun.jvmstat.monitor.VmIdentifier",
                        new Class[] { String.class }, new Object[] { str1 }, JVMToolClassloader);
                // Object localMonitoredVm =
                // ((MonitoredHost)localObject1).getMonitoredVm(localVmIdentifier,
                // 0);
                Object localMonitoredVm = ReflectionHelper.invoke("sun.jvmstat.monitor.MonitoredHost", localObject1,
                        "getMonitoredVm", new Class<?>[] { vmIdentifierClass }, new Object[] { localVmIdentifier },
                        JVMToolClassloader);

                // String mainClass=
                // MonitoredVmUtil.mainClass(localMonitoredVm,true);
                String mainClass = (String) ReflectionHelper.invokeStatic("sun.jvmstat.monitor.MonitoredVmUtil",
                        "mainClass", new Class<?>[] { monitoredVmClass, boolean.class },
                        new Object[] { localMonitoredVm, true }, JVMToolClassloader);
                // String mainArgs= MonitoredVmUtil.mainArgs(localMonitoredVm);
                String mainArgs = (String) ReflectionHelper.invokeStatic("sun.jvmstat.monitor.MonitoredVmUtil", "mainArgs",
                        new Class<?>[] { monitoredVmClass }, new Object[] { localMonitoredVm }, JVMToolClassloader);
                // str3 = MonitoredVmUtil.jvmArgs(localMonitoredVm);
                String jvmArgs = (String) ReflectionHelper.invokeStatic("sun.jvmstat.monitor.MonitoredVmUtil", "jvmArgs",
                        new Class<?>[] { monitoredVmClass }, new Object[] { localMonitoredVm }, JVMToolClassloader);
                // str3 = MonitoredVmUtil.jvmFlags(localMonitoredVm);
                String jvmFlags = (String) ReflectionHelper.invokeStatic("sun.jvmstat.monitor.MonitoredVmUtil", "jvmFlags",
                        new Class<?>[] { monitoredVmClass }, new Object[] { localMonitoredVm }, JVMToolClassloader);

                Map<String, String> map = new LinkedHashMap<String, String>();

                map.put("pid", localIterator.toString());
                map.put("main", mainClass);
                map.put("margs", mainArgs);
                map.put("jargs", jvmArgs);
                map.put("jflags", jvmFlags);

                vms.add(map);

            }
            catch (Exception e) {
                // ignore
                e.printStackTrace();
            }
        }

        return vms;
    }

    /**
     * isWindows
     * 
     * @return
     */
    public static boolean isWindows() {

        return (osname.indexOf("win") > -1) ? true : false;
    }
	
	/**
     * isLinux
     * 
     * @return
     */
    public static boolean isLinux() {

        return (osname.indexOf("linux") > -1) ? true : false;
    }
	
    /**
     * getLineSeperator
     * 
     * @return
     */
    public static String getLineSeperator() {

        String marker = "\r\n";
        if (!isWindows()) {
            marker = "\n";
        }

        return marker;
    }

    // ---------------------------------------JVM Tool Helper API 2.0-------------------------------
    private static final Set<String> minorGC = new HashSet<String>();
    private static final Set<String> fullGC = new HashSet<String>();

    static {

        // Oracle (Sun) HotSpot
        // -XX:+UseSerialGC
        minorGC.add("Copy");
        // -XX:+UseParNewGC
        minorGC.add("ParNew");
        // -XX:+UseParallelGC
        minorGC.add("PS Scavenge");
        // Oracle (BEA) JRockit
        // -XgcPrio:pausetime
        minorGC.add("Garbage collection optimized for short pausetimes Young Collector");
        // -XgcPrio:throughput
        minorGC.add("Garbage collection optimized for throughput Young Collector");
        // -XgcPrio:deterministic
        minorGC.add("Garbage collection optimized for deterministic pausetimes Young Collector");
        // -XX:+UseG1GC
        minorGC.add("G1 Young Generation");
        
        // Oracle (Sun) HotSpot
        // -XX:+UseSerialGC
        fullGC.add("MarkSweepCompact");
        // -XX:+UseParallelGC and (-XX:+UseParallelOldGC or -XX:+UseParallelOldGCCompacting)
        fullGC.add("PS MarkSweep");
        // -XX:+UseConcMarkSweepGC
        fullGC.add("ConcurrentMarkSweep");
        // -XX:+UseG1GC
        fullGC.add("G1 Old Generation");

        // Oracle (BEA) JRockit
        // -XgcPrio:pausetime
        fullGC.add("Garbage collection optimized for short pausetimes Old Collector");
        // -XgcPrio:throughput
        fullGC.add("Garbage collection optimized for throughput Old Collector");
        // -XgcPrio:deterministic
        fullGC.add("Garbage collection optimized for deterministic pausetimes Old Collector");

    }

    public static Map<String, Object> readGCUsage(ObjectInstance oi, MBeanServerConnection mbsc) {

        Map<String, Object> m = new LinkedHashMap<String, Object>();

        ObjectName on = oi.getObjectName();

        String name = on.getKeyProperty("name");

        String gcName = null;

        if (minorGC.contains(name)) {
            gcName = "mgc";

        }
        else if (fullGC.contains(name)) {
            gcName = "fgc";
        }

        m.put(gcName + "_count", getMBeanAttrValue(mbsc, on, "CollectionCount"));
        m.put(gcName + "_time", getMBeanAttrValue(mbsc, on, "CollectionTime"));

        return m;
    }

    public static Map<String, Long> readGCUsage(List<GarbageCollectorMXBean> gcmbList) {

        Map<String, Long> m = new LinkedHashMap<String, Long>();

        for (GarbageCollectorMXBean gcmb : gcmbList) {

            String name = gcmb.getName();
            String gcName = null;
            if (minorGC.contains(name)) {
                gcName = "mgc";

            }
            else if (fullGC.contains(name)) {
                gcName = "fgc";
            }

            if (gcName == null) {
                continue;
            }

            m.put(gcName + "_count", gcmb.getCollectionCount());
            m.put(gcName + "_time", gcmb.getCollectionTime());
        }

        return m;
    }

    public static Map<String, Object> readHeapPoolUsage(ObjectInstance oi, MBeanServerConnection mbsc) {

        Map<String, Object> m = new LinkedHashMap<String, Object>();

        ObjectName on = oi.getObjectName();

        String jvmMemPoolName = getHeapPoolName(on.getKeyProperty("name"));

        m.put(jvmMemPoolName + "_use", getMBeanAttrValue(mbsc, on, "Usage", "used"));
        m.put(jvmMemPoolName + "_commit", getMBeanAttrValue(mbsc, on, "Usage", "committed"));
        m.put(jvmMemPoolName + "_max", getMBeanAttrValue(mbsc, on, "Usage", "max"));
        m.put(jvmMemPoolName + "_init", getMBeanAttrValue(mbsc, on, "Usage", "init"));

        return m;
    }

    public static Map<String, Long> readHeapPoolUsage(List<MemoryPoolMXBean> pmbList) {

        Map<String, Long> m = new LinkedHashMap<String, Long>();

        /*for (MemoryPoolMXBean mpmb : pmbList) {

            String jvmMemPoolName = getHeapPoolName(mpmb.getName().trim());

            MemoryUsage mu = mpmb.getUsage();

            m.put(jvmMemPoolName + "_use", mu.getUsed());
            m.put(jvmMemPoolName + "_commit", mu.getCommitted());
            m.put(jvmMemPoolName + "_max", mu.getMax());
            m.put(jvmMemPoolName + "_init", mu.getInit());
        }*/
        
        Set<String> addedSet = new HashSet<String>();
        
        for (MemoryPoolMXBean mpmb : pmbList) {

            String jvmMemPoolName = getHeapPoolName(mpmb.getName().trim());
            MemoryUsage mu = mpmb.getUsage();
            
            if(addedSet.contains(jvmMemPoolName)) {
            
                m.put(jvmMemPoolName + "_use", (Long)m.get(jvmMemPoolName + "_use") + mu.getUsed());
                m.put(jvmMemPoolName + "_commit", (Long)m.get(jvmMemPoolName + "_commit") + mu.getCommitted());
                m.put(jvmMemPoolName + "_max", (Long)m.get(jvmMemPoolName + "_max") + mu.getMax());
                m.put(jvmMemPoolName + "_init", (Long)m.get(jvmMemPoolName + "_init") + mu.getInit());
            }else {
                
                addedSet.add(jvmMemPoolName);
                m.put(jvmMemPoolName + "_use", mu.getUsed());
                m.put(jvmMemPoolName + "_commit", mu.getCommitted());
                m.put(jvmMemPoolName + "_max", mu.getMax());
                m.put(jvmMemPoolName + "_init", mu.getInit());
            }          
        }

        return m;
    }

    private static String getHeapPoolName(String poolName) {

        String jvmMemPoolName = poolName.toLowerCase();

        if (jvmMemPoolName.indexOf("code") > -1) {
            return "code";
        }
        else if (jvmMemPoolName.indexOf("old") > -1 || jvmMemPoolName.indexOf("tenured") > -1) {
            return "old";
        }
        else if (jvmMemPoolName.indexOf("eden") > -1) {
            return "eden";
        }
        else if (jvmMemPoolName.indexOf("survivor") > -1) {
            return "surv";
        }
        else if (jvmMemPoolName.indexOf("perm") > -1 || jvmMemPoolName.indexOf("metaspace") > -1) {
            return "perm";
        }
        else if (jvmMemPoolName.indexOf("compressed") > -1 && jvmMemPoolName.indexOf("class") > -1) {
            return "compressed";
        }

        return jvmMemPoolName;
    }

    public static Map<String, Object> readClassLoadUsage(ObjectInstance oi, MBeanServerConnection mbsc) {

        Map<String, Object> m = new LinkedHashMap<String, Object>();

        ObjectName on = oi.getObjectName();

        m.put("class_total", getMBeanAttrValue(mbsc, on, "TotalLoadedClassCount"));
        m.put("class_load", getMBeanAttrValue(mbsc, on, "LoadedClassCount"));
        m.put("class_unload", getMBeanAttrValue(mbsc, on, "UnloadedClassCount"));

        return m;
    }

    public static Map<String, Long> readClassLoadUsage(ClassLoadingMXBean clmb) {

        Map<String, Long> m = new LinkedHashMap<String, Long>();

        m.put("class_total", clmb.getTotalLoadedClassCount());
        m.put("class_load", new Long(clmb.getLoadedClassCount()));
        m.put("class_unload", clmb.getUnloadedClassCount());

        return m;
    }

    public static Map<String, Object> readHeapUsage(ObjectInstance oi, MBeanServerConnection mbsc) {

        Map<String, Object> m = new LinkedHashMap<String, Object>();

        ObjectName on = oi.getObjectName();

        m.put("heap_use", getMBeanAttrValue(mbsc, on, "HeapMemoryUsage", "used"));
        m.put("heap_commit", getMBeanAttrValue(mbsc, on, "HeapMemoryUsage", "committed"));
        m.put("heap_max", getMBeanAttrValue(mbsc, on, "HeapMemoryUsage", "max"));
        m.put("heap_init", getMBeanAttrValue(mbsc, on, "HeapMemoryUsage", "init"));

        return m;
    }

    public static Map<String, Long> readHeapUsage(MemoryUsage mu) {

        Map<String, Long> m = new LinkedHashMap<String, Long>();

        m.put("heap_use", mu.getUsed());
        m.put("heap_commit", mu.getCommitted());
        m.put("heap_max", mu.getMax());
        m.put("heap_init", mu.getInit());

        return m;
    }

    public static Map<String, Object> readNonHeapUsage(ObjectInstance oi, MBeanServerConnection mbsc) {

        Map<String, Object> m = new LinkedHashMap<String, Object>();

        ObjectName on = oi.getObjectName();

        m.put("noheap_use", getMBeanAttrValue(mbsc, on, "NonHeapMemoryUsage", "used"));
        m.put("noheap_commit", getMBeanAttrValue(mbsc, on, "NonHeapMemoryUsage", "committed"));
        m.put("noheap_max", getMBeanAttrValue(mbsc, on, "NonHeapMemoryUsage", "max"));
        m.put("noheap_init", getMBeanAttrValue(mbsc, on, "NonHeapMemoryUsage", "init"));

        return m;
    }

    public static Map<String, Long> readNonHeapUsage(MemoryUsage mu) {

        Map<String, Long> m = new LinkedHashMap<String, Long>();

        m.put("noheap_use", mu.getUsed());
        m.put("noheap_commit", mu.getCommitted());
        m.put("noheap_max", mu.getMax());
        m.put("noheap_init", mu.getInit());

        return m;
    }

    public static Map<String, Object> readThreadUsage(ObjectInstance oi, MBeanServerConnection mbsc) {

        Map<String, Object> m = new LinkedHashMap<String, Object>();

        ObjectName on = oi.getObjectName();

        m.put("thread_live", getMBeanAttrValue(mbsc, on, "ThreadCount"));
        m.put("thread_daemon", getMBeanAttrValue(mbsc, on, "DaemonThreadCount"));
        m.put("thread_peak", getMBeanAttrValue(mbsc, on, "PeakThreadCount"));
        m.put("thread_started", getMBeanAttrValue(mbsc, on, "TotalStartedThreadCount"));

        return m;
    }

    public static Map<String, Long> readThreadUsage(ThreadMXBean tmb) {

        Map<String, Long> m = new LinkedHashMap<String, Long>();

        m.put("thread_live", new Long(tmb.getThreadCount()));
        m.put("thread_daemon", new Long(tmb.getDaemonThreadCount()));
        m.put("thread_peak", new Long(tmb.getPeakThreadCount()));
        m.put("thread_started", tmb.getTotalStartedThreadCount());

        return m;
    }

    public static Map<String, Object> readCPUUsage(ObjectInstance oi, MBeanServerConnection mbsc) {

        Map<String, Object> m = new LinkedHashMap<String, Object>();

        ObjectName on = oi.getObjectName();

        Object procCPU = getMBeanAttrValue(mbsc, on, "ProcessCpuLoad");
        Object systemCPU = getMBeanAttrValue(mbsc, on, "SystemCpuLoad");

        if (procCPU == null) {
            procCPU = new Long(-1);
            systemCPU = new Long(-1);
        }
        else {
            DecimalFormat formatter = new DecimalFormat("00.0");

            procCPU = Double.valueOf(formatter.format((Double) procCPU * 100));
            systemCPU = Double.valueOf(formatter.format((Double) systemCPU * 100));
        }

        m.put("cpu_p", procCPU);
        m.put("cpu_s", systemCPU);

        return m;
    }

    public static Map<String, Double> readCPUUsage(OperatingSystemMXBean osMBean) {

        Map<String, Double> m = new LinkedHashMap<String, Double>();

        Double procCPU = (Double) ReflectionHelper.invoke("com.sun.management.OperatingSystemMXBean", osMBean,
                "getProcessCpuLoad", null, null);
        Double systemCPU = (Double) ReflectionHelper.invoke("com.sun.management.OperatingSystemMXBean", osMBean,
                "getSystemCpuLoad", null, null);

        if (procCPU == null) {
            procCPU = getProcessCpuUtilization();
            systemCPU = -1D;
        }

        DecimalFormat formatter = new DecimalFormat("00.0");

        m.put("cpu_p", Double.valueOf(formatter.format(procCPU * 100)));
        m.put("cpu_s", Double.valueOf(formatter.format(systemCPU * 100)));

        return m;
    }

    /**
     * obtain current process cpu utilization if jdk version is 1.6 by-hongqiangwei
     */
    @SuppressWarnings("restriction")
    public static double getProcessCpuUtilization() {

        com.sun.management.OperatingSystemMXBean osMBean = (com.sun.management.OperatingSystemMXBean) ManagementFactory
                .getOperatingSystemMXBean();
        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();

        long processCpuTime1 = osMBean.getProcessCpuTime();
        long runtime1 = runtimeMXBean.getUptime();

        ThreadHelper.suspend(50);

        long processCpuTime2 = osMBean.getProcessCpuTime();
        long runtime2 = runtimeMXBean.getUptime();

        long deltaProcessTime = processCpuTime2 - processCpuTime1;
        long deltaRunTime = (runtime2 - runtime1) * 1000000L;
        int cpuNumber = Runtime.getRuntime().availableProcessors();
        double cpuUtilization = (double) deltaProcessTime / (deltaRunTime * cpuNumber);

        return cpuUtilization;
    }

    private static Object getMBeanAttrValue(MBeanServerConnection mbsc, ObjectName on, String attr, String key) {

        Object o = getMBeanAttrValue(mbsc, on, attr);

        if (o == null) {
            return null;
        }

        if (CompositeData.class.isAssignableFrom(o.getClass())) {

            CompositeData cd = (CompositeData) o;

            return cd.get(key);
        }

        return null;
    }

    private static Object getMBeanAttrValue(MBeanServerConnection mbsc, ObjectName on, String attr) {

        try {
            return mbsc.getAttribute(on, attr);
        }
        catch (AttributeNotFoundException e) {
            // ignore
        }
        catch (InstanceNotFoundException e) {
            // ignore
        }
        catch (MBeanException e) {
            // ignore
        }
        catch (ReflectionException e) {
            // ignore
        }
        catch (IOException e) {
            // ignore
        }

        return null;
    }

    /**
     * 获取当前进程id
     * 
     * @return
     */
    public static String getCurrentProcId() {

        String name = ManagementFactory.getRuntimeMXBean().getName();

        String pid = name.split("@")[0];

        return pid;
    }

}

/*
 * Copyright 2007 The Kuali Foundation
 * 
 * Licensed under the Educational Community License, Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.opensource.org/licenses/ecl1.php
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS
 * IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */
package org.kuali.rice.test;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Properties;
import java.util.Set;

import javax.xml.namespace.QName;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.junit.After;
import org.junit.Before;
import org.kuali.rice.config.Config;
import org.kuali.rice.config.SimpleConfig;
import org.kuali.rice.core.Core;
import org.kuali.rice.lifecycle.BaseLifecycle;
import org.kuali.rice.lifecycle.Lifecycle;
import org.kuali.rice.resourceloader.SpringResourceLoader;
import org.kuali.rice.test.data.PerSuiteUnitTestData;
import org.kuali.rice.test.lifecycles.PerSuiteDataLoaderLifecycle;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

/**
 * Useful superclass for all Rice test cases. Handles setup of test utilities and a test environment. Configures the
 * Spring test environment providing a template method for custom context files in test mode. Also provides a template method
 * for running custom transactional setUp. Tear down handles automatic tear down of objects created inside the test
 * environment.
 * 
 * @author Kuali Rice Team (kuali-rice@googlegroups.com)
 * @since 0.9
 */
public abstract class RiceTestCase extends BaseRiceTestCase {

    private static final Logger LOG = Logger.getLogger(RiceTestCase.class);

    private static final String ALT_LOG4J_CONFIG_LOCATION_PROP = "alt.log4j.config.location";
    private static final String DEFAULT_LOG4J_CONFIG = "classpath:rice-testharness-default-log4j.properties";
    protected static final String DEFAULT_TEST_HARNESS_SPRING_BEANS = "classpath:TestHarnessSpringBeans.xml";
    protected static boolean SUITE_LIFE_CYCLES_RAN = false;

    protected List<Lifecycle> perTestLifeCycles = new LinkedList<Lifecycle>();

    protected List<Lifecycle> suiteLifeCycles = new LinkedList<Lifecycle>();

    private static Set<String> perSuiteDataLoaderLifecycleNamesRun = new HashSet<String>();

    private List<String> reports = new ArrayList<String>();

    private SpringResourceLoader testHarnessSpringResourceLoader;

    /**
     * Whether the test environment is in a "dirty" state.  Each time the unit test starts up
     * dirty is set to true.  If a subclass installs the {@link TransactionalLifecycle} then
     * it should clear the dirty flag.  This flag can be used to perform cleanup in case a previous
     * test left the test environment in a "dirty" state.
     */
    protected static boolean dirty = false;

    @Before
    public void setUp() throws Exception {
        try {
            configureLogging();
            logBeforeRun();

            final long initTime = System.currentTimeMillis();

            setUpInternal();

            report("Time to start all Lifecycles: " + (System.currentTimeMillis() - initTime));
        } catch (Throwable e) {
            e.printStackTrace();
            tearDown();
            throw new RuntimeException(e);
        }
    }

    /**
     * Internal setUp() implementation which is invoked by the main setUp() and wrapped
     * with exception handling.  Subclasses should override this method if they want to
     * add set up steps that should occur in the standard set up process, wrapped by
     * exception handling.
     */
    protected void setUpInternal() throws Exception {
        assertNotNull(getModuleName());
        setModuleName(getModuleName());
        setBaseDirSystemProperty(getModuleName());

        this.perTestLifeCycles = getPerTestLifecycles();
        this.suiteLifeCycles = getSuiteLifecycles();

        if (!SUITE_LIFE_CYCLES_RAN) {
            startLifecycles(this.suiteLifeCycles);
            SUITE_LIFE_CYCLES_RAN = true;
        }

        startSuiteDataLoaderLifecycles();

        startLifecycles(this.perTestLifeCycles);
        
        dirty = true;
    }

    /**
     * This block is walking up the class hierarchy looking for PerSuiteUnitTestData annotations. If it finds one,
     * it will run it once, then add it to a set so that it does not get run again. This is needed so that multiple 
     * tests can extend from the same suite and so that there can be multiple suites throughout the test source branch.
     * 
     * @throws Exception if a PerSuiteDataLoaderLifecycle is unable to be started
     */
    protected void startSuiteDataLoaderLifecycles() throws Exception {
        // get a list of all classes the current class extends from that use the PerSuiteUnitTestData annotation
        List<Class> classesToCheck = new ArrayList<Class>();
        // here we get the list apart checking the annotations to support the perSuiteDataLoaderLifecycleNamesRun variable better
        if (getClass().isAnnotationPresent(PerSuiteUnitTestData.class)) {
            Class clazz = getClass();
            superClassLoop: while (!clazz.getName().equals(Object.class.getName())) {
                for (Annotation annotation : clazz.getDeclaredAnnotations()) {
                    // check if the annotation is a PerSuiteUnitTestData annotation
                    if (annotation.annotationType().getName().equals(PerSuiteUnitTestData.class.getName())) {
                        classesToCheck.add(0, clazz);
                        // now check to see if annotation overrides super class implementations
                        if (((PerSuiteUnitTestData) annotation).overrideSuperClasses()) {
                            break superClassLoop;
                        }
                    }
                }
                clazz = clazz.getSuperclass();
            }
        }
        for (Class clazz : classesToCheck) {
            for (Annotation annotation : clazz.getDeclaredAnnotations()) {
                if (!perSuiteDataLoaderLifecycleNamesRun.contains(clazz.getName())) {
                    new PerSuiteDataLoaderLifecycle(clazz).start();
                }
            }
            perSuiteDataLoaderLifecycleNamesRun.add(clazz.getName());
        }
    }

    /**
     * maven will set this property and find resources from the config based on it. This makes eclipse testing work because
     * we have to put the basedir in our config files in order to find things when testing from maven
     */
    protected void setBaseDirSystemProperty(String moduleBaseDir) {
        if (System.getProperty("basedir") == null) {
            System.setProperty("basedir", System.getProperty("user.dir") + "/" + moduleBaseDir);
        }
    }

    /**
     * Returns the basedir for the module under which the tests are currently executing.
     */
    protected String getBaseDir() {
        return System.getProperty("basedir");
    }

    protected void setModuleName(String moduleName) {
        if (System.getProperty("module.name") == null) {
            System.setProperty("module.name", moduleName);
        }
    }

    @After
    public void tearDown() throws Exception {
        stopLifecycles(this.perTestLifeCycles);
        logAfterRun();
    }

    protected void logBeforeRun() {
        LOG.info("##############################################################");
        LOG.info("# Starting test " + getClass().getSimpleName() + "." + getName() + "...");
        LOG.info("# " + dumpMemory());
        LOG.info("##############################################################");
    }

    protected void logAfterRun() {
        LOG.info("##############################################################");
        LOG.info("# ...finished test " + getClass().getSimpleName() + "." + getName());
        LOG.info("# " + dumpMemory());
        for (final String report : this.reports) {
            LOG.info("# " + report);
        }
        LOG.info("##############################################################\n\n\n");
    }

	protected void configureLogging() throws IOException {
        ResourceLoader resourceLoader = new FileSystemResourceLoader();
        String altLog4jConfigLocation = System.getProperty(ALT_LOG4J_CONFIG_LOCATION_PROP);
        Resource log4jConfigResource = null;
        if (!StringUtils.isEmpty(altLog4jConfigLocation)) { 
            log4jConfigResource = resourceLoader.getResource(altLog4jConfigLocation);
        }
        if (log4jConfigResource == null || !log4jConfigResource.exists()) {
            System.out.println("Alternate Log4j config resource does not exist! " + altLog4jConfigLocation);
            System.out.println("Using default log4j configuration: " + DEFAULT_LOG4J_CONFIG);
            log4jConfigResource = resourceLoader.getResource(DEFAULT_LOG4J_CONFIG);
        } else {
            System.out.println("Using alternate log4j configuration at: " + altLog4jConfigLocation);
        }
        Properties p = new Properties();
        p.load(log4jConfigResource.getInputStream());
        PropertyConfigurator.configure(p);
    }

    protected void startLifecycles(List<Lifecycle> lifecycles) throws Exception {
        for (Lifecycle lifecycle : lifecycles) {
            lifecycle.start();
        }
    }

    protected void stopLifecycles(List<Lifecycle> lifecycles) throws Exception {
        final ListIterator<Lifecycle> iter = lifecycles.listIterator();
        while (iter.hasNext()) {
            iter.next();
        }
        while (iter.hasPrevious()) {
            final Lifecycle lifeCycle = iter.previous();
            try {
                lifeCycle.stop();
            } catch (Exception e) {
                LOG.warn("Failed to shutdown one of the lifecycles!", e);
            }
        }
    }

    /**
     * Returns initial lifecycles up to but excluding the ClearDatabaseLifecycle
     * @return the initial lifecycles up to but excluding the ClearDatabaseLifecycle
     */
    protected List<Lifecycle> getInitialLifecycles() {
        List<Lifecycle> lifecycles = new LinkedList<Lifecycle>();
        lifecycles.add(new BaseLifecycle() {
            public void start() throws Exception {
                Config config = getTestHarnessConfig();
                Core.init(config);
                super.start();
            }
        });
        lifecycles.add(getTestHarnessSpringResourceLoader());
        lifecycles.add(new BaseLifecycle() {
            public void start() throws Exception {
                TestHarnessServiceLocator.setContext(getTestHarnessSpringResourceLoader().getContext());
                super.start();
            }
        });
        lifecycles.add(new DerbyDBCreationLifecycle(getDerbySQLFileLocation()));

        return lifecycles;
    }

    /**
     * @return the default lifecycles this class defines
     */
    protected List<Lifecycle> getDefaultSuiteLifecycles() {
        List<Lifecycle> lifecycles = getInitialLifecycles();
        lifecycles.add(new ClearDatabaseLifecycle(getTablesToClear(), getTablesNotToClear()));
        // lifecycles.add(new PerSuiteDataLoaderLifecycle(getClass()));
        return lifecycles;
    }
    
    /**
     * @return Lifecycles run once during the suite
     */
    protected List<Lifecycle> getSuiteLifecycles() {
        return getDefaultSuiteLifecycles();
    }

    /**
     * @return the default lifecycles this class defines
     */
    protected List<Lifecycle> getDefaultPerTestLifecycles() {
        List<Lifecycle> lifecycles = new LinkedList<Lifecycle>();
        lifecycles.add(getPerTestDataLoaderLifecycle());
        return lifecycles;
    }

    /**
     * @return Lifecycles run every test run
     */
    protected List<Lifecycle> getPerTestLifecycles() {
        return getDefaultPerTestLifecycles();
    }

    protected List<String> getTablesToClear() {
        return new ArrayList<String>();
    }

    protected List<String> getTablesNotToClear() {
        return new ArrayList<String>();
    }

    protected void report(final String report) {
        this.reports.add(report);
    }

    protected String dumpMemory() {
        final long total = Runtime.getRuntime().totalMemory();
        final long free = Runtime.getRuntime().freeMemory();
        final long max = Runtime.getRuntime().maxMemory();
        return "[Memory] max: " + max + ", total: " + total + ", free: " + free;
    }

    public SpringResourceLoader getTestHarnessSpringResourceLoader() {
        if (testHarnessSpringResourceLoader == null) {
            testHarnessSpringResourceLoader = new SpringResourceLoader(new QName("TestHarnessSpringContext"), getTestHarnessSpringBeansLocation());
        }
        return testHarnessSpringResourceLoader;
    }

    /**
     * Returns the location of the test harness spring beans context file.
     * Subclasses may override to specify a different location.
     * @return the location of the test harness spring beans context file.
     */
    protected String[] getTestHarnessSpringBeansLocation() {
        String[] locations;
        String moduleTestHarnessSpringBeansPath = getModuleName().toUpperCase() + "TestHarnessSpringBeans.xml";
        LOG.debug("Looking for module override test harness spring beans: " + moduleTestHarnessSpringBeansPath);
        Resource resource = new ClassPathResource(moduleTestHarnessSpringBeansPath);
        if (resource.exists()) {
            LOG.debug("Found: " + resource);
            locations = new String[] { DEFAULT_TEST_HARNESS_SPRING_BEANS, "classpath:" + moduleTestHarnessSpringBeansPath };  
        } else {
            LOG.debug("Not found: " + resource);
            locations = new String[] { DEFAULT_TEST_HARNESS_SPRING_BEANS };
        }
        return locations;
    }

    protected Config getTestHarnessConfig() throws Exception {
        Config config = new SimpleConfig(getConfigLocations(), System.getProperties());
        config.parseConfig();
        return config;
    }

    /**
     * Subclasses may override this method to customize the location(s) of the Rice configuration.
     * By default it is: classpath:META-INF/" + getModuleName().toLowerCase() + "-test-config.xml"
     * @return List of config locations to add to this tests config location.
     */
    protected List<String> getConfigLocations() {
        List<String> configLocations = new ArrayList<String>();
        configLocations.add(getRiceMasterDefaultConfigFile());
        configLocations.add("classpath:META-INF/" + getModuleName().toLowerCase() + "-test-config.xml");
        return configLocations;
    }
    
    protected String getRiceMasterDefaultConfigFile() {
        return "classpath:META-INF/test-config-defaults.xml";
    }

    /**
     * same as the module directory in the project.
     * 
     * @return name of module that the tests located
     */
    protected abstract String getModuleName();

    /**
     * Note: We may want to make this more automagical and base this off of convention from the module name Typical return
     * value "classpath:db/derby/testharness.sql"
     * 
     * @return location of sql file containing ddl for a derby db to be ran before start up.
     */
    protected String getDerbySQLFileLocation() {
        return null;
    }

}
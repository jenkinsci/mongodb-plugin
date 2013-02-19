package org.jenkinsci.plugins.mongodb;

import hudson.model.FreeStyleProject;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.recipes.LocalData;

/**
 * @author Kiyotaka Oku
 */
public class CompatibilityTest extends HudsonTestCase {

    @LocalData
    public void test_1_2_to_1_3() {

        MongoDBInstallation[] installations = hudson.getDescriptorByType(MongoDBInstallation.DescriptorImpl.class).getInstallations();
        assertEquals(1, installations.length);

        MongoDBInstallation inst = installations[0];
        assertEquals(null, inst.getParameters());
        assertEquals(0, inst.getStartTimeout());

        FreeStyleProject job = (FreeStyleProject) hudson.getItem("test");
        MongoBuildWrapper wrapper = (MongoBuildWrapper) job.getBuildWrappers().get(hudson.getDescriptor(MongoBuildWrapper.class));

        assertNotNull(wrapper);
        assertEquals("mongo", wrapper.getMongodbName());
        assertEquals("data", wrapper.getDbpath());
        assertEquals("10000", wrapper.getPort());
        assertNull(wrapper.getParameters());
        assertEquals(0, wrapper.getStartTimeout());
    }
}

package org.jenkinsci.plugins.mongodb;

import hudson.model.FreeStyleProject;

import org.junit.Assert;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;

/**
 * @author Kiyotaka Oku
 */
public class CompatibilityTest extends JenkinsRule {

    @LocalData
    public void test_1_2_to_1_3() {

        MongoDBInstallation[] installations = jenkins.getDescriptorByType(MongoDBInstallation.DescriptorImpl.class).getInstallations();
        Assert.assertEquals(1, installations.length);

        MongoDBInstallation inst = installations[0];
        Assert.assertEquals(null, inst.getParameters());
        Assert.assertEquals(0, inst.getStartTimeout());

        FreeStyleProject job = (FreeStyleProject) jenkins.getItem("test");
        MongoBuildWrapper wrapper = (MongoBuildWrapper) job.getBuildWrappers().get(jenkins.getDescriptor(MongoBuildWrapper.class));

        Assert.assertNotNull(wrapper);
        Assert.assertEquals("mongo", wrapper.getMongodbName());
        Assert.assertEquals("data", wrapper.getDbpath());
        Assert.assertEquals("10000", wrapper.getPort());
        Assert.assertNull(wrapper.getParameters());
        Assert.assertEquals(0, wrapper.getStartTimeout());
    }
}

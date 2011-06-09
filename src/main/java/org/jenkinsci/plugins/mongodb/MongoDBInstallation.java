package org.jenkinsci.plugins.mongodb;

import static org.jenkinsci.plugins.mongodb.Messages.MongoDB_NotDirectory;
import static org.jenkinsci.plugins.mongodb.Messages.MongoDB_NotMongoDBDirectory;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.EnvironmentSpecific;
import hudson.model.TaskListener;
import hudson.model.Hudson;
import hudson.model.Node;
import hudson.remoting.Callable;
import hudson.slaves.NodeSpecific;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstaller;
import hudson.tools.ToolProperty;
import hudson.tools.ToolInstallation;
import hudson.util.FormValidation;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

public class MongoDBInstallation extends ToolInstallation implements EnvironmentSpecific<MongoDBInstallation>, NodeSpecific<MongoDBInstallation> {

    @DataBoundConstructor
    public MongoDBInstallation(String name, String home, List<? extends ToolProperty<?>> properties) {
        super(name, home, properties);
    }

    public MongoDBInstallation forNode(Node node, TaskListener log) throws IOException, InterruptedException {
        return new MongoDBInstallation(getName(), translateFor(node, log), getProperties().toList());
    }

    public MongoDBInstallation forEnvironment(EnvVars environment) {
        return new MongoDBInstallation(getName(), environment.expand(getHome()), getProperties().toList());
    }

    public File getExecutable(Launcher launcher) throws IOException, InterruptedException {
        return launcher.getChannel().call(new Callable<File, IOException>() {
            public File call() throws IOException {
                File homeDir = new File(getHome());
                File r = new File(homeDir, "bin/mongod");
                return r.exists() ? r : findExecutable(homeDir);
            }
        });
    }

    protected File findExecutable(File parent) {
        for (File child : parent.listFiles()) {
            if (child.isFile() && child.getName().equals("mongod")) {
                return child;
            } else if (child.isDirectory()) {
                return findExecutable(child);
            }
        }
        return null;
    }

    @Extension
    public static class DescriptorImpl extends ToolDescriptor<MongoDBInstallation> {

        @Override
        public String getDisplayName() {
            return "MongoDB";
        }

        @Override
        public List<? extends ToolInstaller> getDefaultInstallers() {
            return Collections.singletonList(new MongoDBInstaller(null));
        }

        @Override
        public MongoDBInstallation[] getInstallations() {
            return Hudson.getInstance().getDescriptorByType(MongoBuildWrapper.DescriptorImpl.class).getInstallations();
        }

        @Override
        public void setInstallations(MongoDBInstallation... installations) {
            Hudson.getInstance().getDescriptorByType(MongoBuildWrapper.DescriptorImpl.class).setInstallations(installations);
        }

        public static FormValidation doCheckName(@QueryParameter String value) {
            return FormValidation.validateRequired(value);
        }

        public static FormValidation doCheckHome(@QueryParameter File value) {
            if (value.getPath() == "")
                return FormValidation.ok();

            if (!value.isDirectory())
                return FormValidation.error(MongoDB_NotDirectory());

            File mongod = new File(value, "bin/mongod");
            if (!mongod.exists())
                return FormValidation.error(MongoDB_NotMongoDBDirectory(value));

            return FormValidation.ok();
        }
    }
}

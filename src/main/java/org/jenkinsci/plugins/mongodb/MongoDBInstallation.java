package org.jenkinsci.plugins.mongodb;

import static org.apache.commons.lang.StringUtils.isEmpty;
import static org.jenkinsci.plugins.mongodb.Messages.MongoDB_InvalidStartTimeout;
import static org.jenkinsci.plugins.mongodb.Messages.MongoDB_NotDirectory;
import static org.jenkinsci.plugins.mongodb.Messages.MongoDB_NotMongoDBDirectory;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Functions;
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
import hudson.tools.ToolPropertyDescriptor;
import hudson.tools.ToolInstallation;
import hudson.util.DescribableList;
import hudson.util.FormValidation;
import jenkins.security.MasterToSlaveCallable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

public class MongoDBInstallation extends ToolInstallation implements EnvironmentSpecific<MongoDBInstallation>, NodeSpecific<MongoDBInstallation> {

    private String parameters;
	
	private int startTimeout;

	@DataBoundConstructor
    public MongoDBInstallation(String name, String home, List<? extends ToolProperty<?>> properties, String parameters, int startTimeout) {
        super(name, home, properties);
		this.parameters = parameters;
		this.startTimeout = startTimeout;
    }

    public MongoDBInstallation forNode(Node node, TaskListener log) throws IOException, InterruptedException {
        return new MongoDBInstallation(getName(), translateFor(node, log), getProperties().toList(), parameters, startTimeout);
    }

    public MongoDBInstallation forEnvironment(EnvVars environment) {
        return new MongoDBInstallation(getName(), environment.expand(getHome()), getProperties().toList(), parameters, startTimeout);
    }
    
    public String getParameters() {
		return parameters;
	}

	public void setParameters(String parameters) {
		this.parameters = parameters;
	}

	public int getStartTimeout() {
		return startTimeout;
	}

	public void setStartTimeout(int startTimeout) {
		this.startTimeout = startTimeout;
	}


    public String getExecutable(final Launcher launcher) throws IOException, InterruptedException {
        return launcher.getChannel().call(new MasterToSlaveCallable<String, IOException>() {
            public String call() throws IOException {
                File homeDir = new File(getHome());
                if (!(homeDir.exists() && homeDir.isDirectory())) {
                    throw new FileNotFoundException(String.format("No such directory. [%s]", homeDir));
                }
                File r = new File(homeDir, getExeFile());
                File executable = r.exists() ? r : findExecutable(homeDir);
                if (executable == null) {
                    throw new FileNotFoundException(String.format("[%s] is not MongoDB home directory.", homeDir));
                }
                return executable.getPath();
            }
        });
    }

    protected File findExecutable(File parent) {
        for (File child : parent.listFiles()) {
            if (child.isFile() && (parent.getName() + "/" + child.getName()).equals(getExeFile())) {
                return child;
            } else if (child.isDirectory()) {
                File r = findExecutable(child);
                if (r != null) {
                    return findExecutable(child);
                }
            }
        }
        return null;
    }

    protected String getExeFile() {
        return Functions.isWindows() ? "bin/mongod.exe" : "bin/mongod";
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

        @Override
        public FormValidation doCheckName(@QueryParameter String value) {
            return FormValidation.validateRequired(value);
        }
		
		public static FormValidation doCheckStartTimeout(@QueryParameter String value) {
			if(isEmpty(value)) {
        		return FormValidation.ok();
        	}
        	
        	try {
        		int timeout = Integer.parseInt(value);
        		return timeout>=0 ? FormValidation.ok() : FormValidation.error(MongoDB_InvalidStartTimeout());
        	} catch (NumberFormatException e) {
        		return FormValidation.error(MongoDB_InvalidStartTimeout());
        	}
	    }

        @Override
        public FormValidation doCheckHome(@QueryParameter File value) {
            if (StringUtils.isEmpty(value.getPath()))
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

package org.jenkinsci.plugins.mongodb;

import static org.apache.commons.lang.StringUtils.isEmpty;
import static org.jenkinsci.plugins.mongodb.Messages.MongoDB_InvalidPortNumber;
import static org.jenkinsci.plugins.mongodb.Messages.MongoDB_InvalidStartTimeout;
import static org.jenkinsci.plugins.mongodb.Messages.MongoDB_NotDirectory;
import static org.jenkinsci.plugins.mongodb.Messages.MongoDB_NotEmptyDirectory;

import java.io.File;
import java.io.IOException;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;

import hudson.CopyOnWrite;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.FilePath.FileCallable;
import hudson.Launcher;
import hudson.Launcher.ProcStarter;
import hudson.Proc;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Computer;
import hudson.remoting.Callable;
import hudson.remoting.VirtualChannel;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.util.ArgumentListBuilder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import net.sf.json.JSONObject;

public class MongoBuildWrapper extends BuildWrapper {
	
	private String mongodbName;
	private String dbpath;
	private String port;
	private String parameters;
	private int startTimeout;
	
	public MongoBuildWrapper() {
	}
	
	@DataBoundConstructor
	public MongoBuildWrapper(String mongodbName, String dbpath, String port, String parameters, int startTimeout) {
		this.mongodbName = mongodbName;
		this.dbpath = dbpath;
		this.port = port;
		this.startTimeout = startTimeout;
		this.parameters = parameters;
	}
	
	public MongoDBInstallation getMongoDB() {
		for (MongoDBInstallation i : ((DescriptorImpl) getDescriptor()).getInstallations()) {
			if (mongodbName != null && i.getName().equals(mongodbName)) {
				return i;
			}
		}
		return null;
	}
	
	public String getMongodbName() {
		return mongodbName;
	}
	
	public String getDbpath() {
		return dbpath;
	}
	
	public String getPort() {
		return port;
	}
	
	public void setMongodbName(String mongodbName) {
		this.mongodbName = mongodbName;
	}
	
	public void setDbpath(String dbpath) {
		this.dbpath = dbpath;
	}
	
	public void setPort(String port) {
		this.port = port;
	}
	
	public String getParameters() {
		return parameters;
	}
	
	public void setParameters(String parameters) {
		this.parameters = parameters;
	}
	
	/**
	 * The time (in milliseconds) to wait for mongodb to start
	 * 
	 * @return time in milliseconds
	 */
	public int getStartTimeout() {
		return startTimeout;
	}
	
	public void setStartTimeout(int startTimeout) {
		this.startTimeout = startTimeout;
	}
	
	@Override
	public Environment setUp(AbstractBuild build, final Launcher launcher, final BuildListener listener)
			throws IOException, InterruptedException {
		
		EnvVars env = build.getEnvironment(listener);
		
		MongoDBInstallation mongo = getMongoDB().forNode(Computer.currentComputer().getNode(), listener).forEnvironment(
				env);
		ArgumentListBuilder args = new ArgumentListBuilder().add(mongo.getExecutable(launcher));
		String globalParameters = mongo.getParameters();
		int globalStartTimeout = mongo.getStartTimeout();
		final FilePath dbpathFile = setupCmd(launcher, args, build.getWorkspace(), false, globalParameters);
		
		dbpathFile.deleteRecursive();
		dbpathFile.mkdirs();
		return launch(launcher, args, listener, globalStartTimeout);
	}
	
	protected Environment launch(final Launcher launcher, ArgumentListBuilder args, final BuildListener listener,
			int globalStartTimeout) throws IOException, InterruptedException {
		ProcStarter procStarter = launcher.launch().cmds(args);
		log(listener, "Executing mongodb start command: " + procStarter.cmds());
		final Proc proc = procStarter.start();
		
		try {
			
			int effectiveTimeout = globalStartTimeout;
			if (startTimeout > 0) {
				effectiveTimeout = startTimeout;
			}
			
			Boolean startResult = launcher.getChannel().call(new WaitForStartCommand(listener, port, effectiveTimeout));
			if (!startResult) {
				log(listener, "ERROR: Filed to start mongodb");
			}
		} catch (Exception e) {
			e.printStackTrace(listener.getLogger());
			return null;
		}
		
		return new BuildWrapper.Environment() {
			@Override
			public boolean tearDown(AbstractBuild build, BuildListener listener)
					throws IOException, InterruptedException {
				if (proc.isAlive()) {
					log(listener, "Killing mongodb process...");
					proc.kill();
				} else {
					log(listener, "Will not kill mongodb process as it is already dead.");
				}
				return super.tearDown(build, listener);
			}
		};
	}
	
	protected FilePath setupCmd(Launcher launcher, ArgumentListBuilder args, FilePath workspace, boolean fork,
			String globalParameters) throws IOException, InterruptedException {
		
		if (fork) {
			args.add("--fork");
		}
		args.add("--logpath").add(workspace.child("mongodb.log").getRemote());
		
		FilePath dbpathFile;
		if (isEmpty(dbpath)) {
			dbpathFile = workspace.child("data").child("db");
		} else {
			dbpathFile = new FilePath(launcher.getChannel(), dbpath);
			boolean isAbsolute = dbpathFile.act(new IsAbsoluteCheck());
			if (!isAbsolute) {
				dbpathFile = workspace.child(dbpath);
			}
		}
		
		args.add("--dbpath").add(dbpathFile.getRemote());
		
		if (StringUtils.isNotEmpty(port)) {
			args.add("--port", port);
		}
		String effectiveParameters = globalParameters;
		if (StringUtils.isNotEmpty(parameters)) {
			effectiveParameters = parameters;
		}
		
		if (StringUtils.isNotEmpty(effectiveParameters)) {
			for (String parameter : effectiveParameters.split("--")) {
				
				if (parameter.trim().indexOf(" ") != -1) {
					// The parameter is a name value pair e.g. --syncdelay 0
					// The construction is done this way so the case where the value is in quotes and possibly contains
					// space chars is properly handled
					String parameterName = parameter.trim().substring(0, parameter.trim().indexOf(" ")).trim();
					String parameterValue = parameter.trim().substring(parameter.trim().indexOf(" ")).trim();
					if (StringUtils.isNotEmpty(parameterName)) {
						args.add("--" + parameterName, parameterValue);
					}
				} else {
					// No value parameter e.g. --noprealloc
					if (StringUtils.isNotEmpty(parameter.trim())) {
						args.add("--" + parameter.trim());
					}
				}
			}
		}
		
		return dbpathFile;
	}
	
	private static void log(BuildListener listener, String log) {
		listener.getLogger().println(String.format("[MongoDB] %s", log));
	}
	
	private static class WaitForStartCommand implements Callable<Boolean, Exception> {
		
		private final BuildListener listener;
		
		private final String port;
		
		private int startTimeout;
		
		public WaitForStartCommand(BuildListener listener, String port, int startTimeout) {
			this.listener = listener;
			this.port = StringUtils.defaultIfEmpty(port, "27017");
			if (startTimeout == 0) {
				this.startTimeout = 15000;
			} else {
				this.startTimeout = startTimeout;
			}
		}
		
		public Boolean call() throws Exception {
			return waitForStart();
		}
		
		protected boolean waitForStart() throws Exception {
			log(listener, "Starting...");
			MongoClient mongo = null;
			try {
				MongoClientOptions options = MongoClientOptions.builder().serverSelectionTimeout(startTimeout).build();
				mongo = new MongoClient("localhost:" + port, options);
				mongo.listDatabaseNames().first();
				return true;
			} catch (Exception e) {
				throw e;
			} finally {
				if (mongo != null) {
					mongo.close();
				}
			}
		}
	}
	
	@Extension
	public static final class DescriptorImpl extends BuildWrapperDescriptor {
		
		@CopyOnWrite
		private volatile MongoDBInstallation[] installations = new MongoDBInstallation[0];
		
		public DescriptorImpl() {
			super(MongoBuildWrapper.class);
			load();
		}
		
		@Override
		public boolean isApplicable(AbstractProject<?, ?> item) {
			return true;
		}
		
		@Override
		public String getDisplayName() {
			return "MongoDB";
		}
		
		@Override
		public BuildWrapper newInstance(StaplerRequest req, JSONObject formData)
				throws hudson.model.Descriptor.FormException {
			return req.bindJSON(clazz, formData);
		}
		
		public MongoDBInstallation[] getInstallations() {
			return installations;
		}
		
		public void setInstallations(MongoDBInstallation[] installations) {
			this.installations = installations;
			save();
		}
		
		public static FormValidation doCheckStartTimeout(@QueryParameter String value) {
			if (isEmpty(value)) {
				return FormValidation.ok();
			}
			
			try {
				int timeout = Integer.parseInt(value);
				return timeout >= 0 ? FormValidation.ok() : FormValidation.error(MongoDB_InvalidStartTimeout());
			} catch (NumberFormatException e) {
				return FormValidation.error(MongoDB_InvalidStartTimeout());
			}
		}
		
		public static FormValidation doCheckPort(@QueryParameter String value) {
			return isPortNumber(value) ? FormValidation.ok() : FormValidation.error(MongoDB_InvalidPortNumber());
		}
		
		public static FormValidation doCheckDbpath(@QueryParameter String value) {
			if (isEmpty(value)) {
				return FormValidation.ok();
			}
			
			File file = new File(value);
			if (!file.isDirectory()) {
				return FormValidation.error(MongoDB_NotDirectory());
			}
			
			if (file.list().length > 0) {
				return FormValidation.warning(MongoDB_NotEmptyDirectory());
			}
			
			return FormValidation.ok();
		}
		
		protected static boolean isPortNumber(String value) {
			if (isEmpty(value)) {
				return true;
			}
			if (StringUtils.isNumeric(value)) {
				int num = Integer.parseInt(value);
				return num >= 0 && num <= 65535;
			}
			return false;
		}
		
		public ListBoxModel doFillMongodbNameItems() {
			ListBoxModel m = new ListBoxModel();
			for (MongoDBInstallation inst : installations) {
				m.add(inst.getName());
			}
			return m;
		}
	}
	
	private static class IsAbsoluteCheck implements FileCallable<Boolean> {
		
		public Boolean invoke(File f, VirtualChannel channel) {
			return f.isAbsolute();
		}
	}
}

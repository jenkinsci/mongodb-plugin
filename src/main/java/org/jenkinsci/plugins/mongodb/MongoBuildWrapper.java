package org.jenkinsci.plugins.mongodb;

import static org.apache.commons.lang.StringUtils.isEmpty;
import static org.jenkinsci.plugins.mongodb.Messages.MongoDB_InvalidPortNumber;
import static org.jenkinsci.plugins.mongodb.Messages.MongoDB_NotDirectory;
import static org.jenkinsci.plugins.mongodb.Messages.MongoDB_NotEmptyDirectory;
import hudson.CopyOnWrite;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.FilePath.FileCallable;
import hudson.Launcher;
import hudson.Launcher.ProcStarter;
import hudson.Proc;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Computer;
import hudson.remoting.Callable;
import hudson.remoting.VirtualChannel;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.util.ArgumentListBuilder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

public class MongoBuildWrapper extends BuildWrapper {

    private String mongodbName;
    private String dbpath;
    private String port;

    public MongoBuildWrapper() {}

    @DataBoundConstructor
    public MongoBuildWrapper(String mongodbName, String dbpath, String port) {
        this.mongodbName = mongodbName;
        this.dbpath = dbpath;
        this.port = port;
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

    @Override
    public Environment setUp(AbstractBuild build, final Launcher launcher, final BuildListener listener) throws IOException, InterruptedException {

        EnvVars env = build.getEnvironment(listener);

        MongoDBInstallation mongo = getMongoDB()
            .forNode(Computer.currentComputer().getNode(), listener)
            .forEnvironment(env);
        ArgumentListBuilder args = new ArgumentListBuilder().add(mongo.getExecutable(launcher));
        final FilePath dbpathFile = setupCmd(launcher,args, build.getWorkspace(), false);

    	dbpathFile.deleteRecursive();
    	dbpathFile.mkdirs();
        return launch(launcher, args, listener);
    }

    protected Environment launch(final Launcher launcher, ArgumentListBuilder args, final BuildListener listener) throws IOException, InterruptedException {
        ProcStarter procStarter = launcher.launch().cmds(args);
        log(listener, "Executing mongodb start command: "+procStarter.cmds());
		final Proc proc = procStarter.start();

        try {
            Boolean startResult = launcher.getChannel().call(new WaitForStartCommand(listener, port));
            if(!startResult) {
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

    protected FilePath setupCmd(Launcher launcher, ArgumentListBuilder args, FilePath workspace, boolean fork) throws IOException, InterruptedException {

        if (fork) {
        	args.add("--fork");
        }
        args.add("--logpath").add(workspace.child("mongodb.log").getRemote());

        FilePath dbpathFile;
        if (isEmpty(dbpath)) {
            dbpathFile = workspace.child("data").child("db");
        } else {
            dbpathFile = new FilePath(launcher.getChannel(),dbpath);
            boolean isAbsolute = dbpathFile.act(new FileCallable<Boolean>() {

				public Boolean invoke(File f, VirtualChannel channel){
					return f.isAbsolute();
				}
			});
            
            if (!isAbsolute) {
                dbpathFile = workspace.child(dbpath);
            }
        }
        
        args.add("--dbpath").add(dbpathFile.getRemote());

        if (StringUtils.isNotEmpty(port)) {
            args.add("--port", port);
        }

        return dbpathFile;
    }

    private static void log(BuildListener listener, String log) {
        listener.getLogger().println(String.format("[MongoDB] %s", log));
    }

    private static class WaitForStartCommand implements Callable<Boolean, Exception> {

        private static final int MAX_RETRY = 5;

        private int retryCount;

        private BuildListener listener;

        private String port;

        public WaitForStartCommand(BuildListener listener, String port) {
            this.listener = listener;
            this.port = StringUtils.defaultIfEmpty(port, "27017");
        }

        public Boolean call() throws Exception {
            return waitForStart();
        }

        protected boolean waitForStart() throws Exception {
            log(listener, "Starting...");
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL("http://localhost:" + port).openConnection();
                if (conn.getResponseCode() == 200) {
                    log(listener, "MongoDB running at:"+new URL("http://localhost:" + port).toString());
                    return true;
                } else {
                    return false;
                }
            } catch (MalformedURLException e) {
                throw e;
            } catch (ConnectException e) {
                try {
                    if (++retryCount <= MAX_RETRY) {
                        Thread.sleep(3000);
                        return waitForStart();
                    } else {
                        return false;
                    }
                } catch (InterruptedException e1) {
                    throw e;
                }
            } catch (IOException e) {
                throw e;
            } finally {
                if (conn != null)
                    conn.disconnect();
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
        public BuildWrapper newInstance(StaplerRequest req, JSONObject formData) throws hudson.model.Descriptor.FormException {
            return req.bindJSON(clazz, formData);
        }

        public MongoDBInstallation[] getInstallations() {
            return installations;
        }

        public void setInstallations(MongoDBInstallation[] installations) {
            this.installations = installations;
            save();
        }

        public static FormValidation doCheckPort(@QueryParameter String value) {
            return isPortNumber(value) ? FormValidation.ok() : FormValidation.error(MongoDB_InvalidPortNumber());
        }

        public static FormValidation doCheckDbpath(@QueryParameter String value) {
            if (isEmpty(value))
                return FormValidation.ok();

            File file = new File(value);
            if (!file.isDirectory())
                return FormValidation.error(MongoDB_NotDirectory());

            if (file.list().length > 0)
                return FormValidation.warning(MongoDB_NotEmptyDirectory());

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
}

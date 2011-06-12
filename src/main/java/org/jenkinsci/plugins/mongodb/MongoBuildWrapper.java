package org.jenkinsci.plugins.mongodb;

import static org.apache.commons.lang.StringUtils.isEmpty;
import static org.jenkinsci.plugins.mongodb.Messages.MongoDB_InvalidPortNumber;
import static org.jenkinsci.plugins.mongodb.Messages.MongoDB_NotDirectory;
import static org.jenkinsci.plugins.mongodb.Messages.MongoDB_NotEmptyDirectory;
import hudson.CopyOnWrite;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Computer;
import hudson.remoting.Callable;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.util.ArgumentListBuilder;
import hudson.util.FormValidation;
import hudson.util.ProcessTree;
import hudson.util.ProcessTree.OSProcess;
import hudson.util.ListBoxModel;

import java.io.File;
import java.io.IOException;

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
    public Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {

        EnvVars env = build.getEnvironment(listener);

        MongoDBInstallation mongo = getMongoDB()
            .forNode(Computer.currentComputer().getNode(), listener)
            .forEnvironment(env);

        ArgumentListBuilder args = new ArgumentListBuilder().add(mongo.getExecutable(launcher));
        final File dbpathFile = setupCmd(args, new File(env.get("WORKSPACE")));

        try {
            launcher.getChannel().call(new DbpathCleaner(dbpathFile));
        } catch (Exception e) {
            e.printStackTrace(listener.getLogger());
        }

        launcher.launch().cmds(args).join();

        return new BuildWrapper.Environment() {
            @Override
            public boolean tearDown(AbstractBuild build, BuildListener listener)
                    throws IOException, InterruptedException {
                String pid = new FilePath(new File(dbpathFile, "mongod.lock")).readToString().trim();
                if (StringUtils.isNotEmpty(pid)) {
                    OSProcess proc = ProcessTree.get().get(Integer.parseInt(pid));
                    if (proc != null) {
                        proc.kill();
                    }
                }
                return super.tearDown(build, listener);
            }
        };
    }

    protected File setupCmd(ArgumentListBuilder args, File workspace) {

        args.add("--fork").add("--logpath").add(new File(workspace, "mongodb.log").getPath());

        File dbpathFile;
        if (isEmpty(dbpath)) {
            dbpathFile = new File(workspace, "data/db");
        } else {
            dbpathFile = new File(dbpath);
            if (!dbpathFile.isAbsolute()) {
                dbpathFile = new File(workspace, dbpath);
            }
        }
        args.add("--dbpath").add(dbpathFile.getPath());

        if (StringUtils.isNotEmpty(port)) {
            args.add("--port", port);
        }

        return dbpathFile;
    }

    private static class DbpathCleaner implements Callable<Void, Exception> {

        private File file;

        public DbpathCleaner(File file) {
            this.file = file;
        }

        public Void call() throws Exception {
            new FilePath(file).deleteRecursive();
            file.mkdirs();
            return null;
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

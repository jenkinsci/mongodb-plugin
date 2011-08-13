package org.jenkinsci.plugins.mongodb;

import hudson.Extension;
import hudson.tools.DownloadFromUrlInstaller;
import hudson.tools.ToolInstallation;

import org.kohsuke.stapler.DataBoundConstructor;

public class MongoDBInstaller extends DownloadFromUrlInstaller {

    @DataBoundConstructor
    public MongoDBInstaller(String id) {
        super(id);
    }

    @Extension
    public static final class DescriptorImpl extends DownloadFromUrlInstaller.DescriptorImpl<MongoDBInstaller> {
        public String getDisplayName() {
            return "Install from mongodb.org";
        }

        @Override
        public boolean isApplicable(Class<? extends ToolInstallation> toolType) {
            return toolType == MongoDBInstallation.class;
        }
    }
}

package org.jenkinsci.plugins.mongodb;

import static java.lang.String.format;
import static org.junit.Assert.assertEquals;
import hudson.util.ArgumentListBuilder;

import java.io.File;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class MongoBuildWrapperTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private File workspace;
    private File buildDir;

    @Before
    public void init() {
        workspace = tempFolder.newFolder("workspace");
        buildDir = tempFolder.newFolder("build");
    }

    @Test
    public void setupCmd_with_defaults() {

        ArgumentListBuilder args = new ArgumentListBuilder();
        File actualDbpath = new MongoBuildWrapper("mongo", null, null)
            .setupCmd(args, workspace, buildDir);

        File expectedDbpath = new File(workspace, "data/db");
        assertEquals(expectedDbpath, actualDbpath);
        assertEquals(format("--fork --logpath %s/mongodb.log --dbpath %s",
                buildDir.getAbsolutePath(),
                expectedDbpath.getAbsolutePath()),
            args.toStringWithQuote());
    }

    @Test
    public void setupCmd_with_dbpath() {

        ArgumentListBuilder args = new ArgumentListBuilder();
        File actualDbpath = new MongoBuildWrapper("mongo", "data_dir", null)
            .setupCmd(args, workspace, buildDir);

        File expectedDbpath = new File(workspace, "data_dir");
        assertEquals(expectedDbpath, actualDbpath);
        assertEquals(format("--fork --logpath %s/mongodb.log --dbpath %s",
                buildDir.getAbsolutePath(),
                expectedDbpath.getAbsolutePath()),
            args.toStringWithQuote());
    }

    @Test
    public void setupCmd_with_abusolute_dbpath() {

        ArgumentListBuilder args = new ArgumentListBuilder();

        String absolutePath = new File(tempFolder.getRoot(), "foo/bar/data_dir").getAbsolutePath();
        File actualDbpath = new MongoBuildWrapper("mongo", absolutePath, null)
            .setupCmd(args, workspace, buildDir);

        assertEquals(new File(absolutePath), actualDbpath);
        assertEquals(format("--fork --logpath %s/mongodb.log --dbpath %s",
                buildDir.getAbsolutePath(),
                absolutePath),
            args.toStringWithQuote());
    }

    @Test
    public void setupCmd_with_port() {

        ArgumentListBuilder args = new ArgumentListBuilder();
        File actualDbpath = new MongoBuildWrapper("mongo", null, "1234")
            .setupCmd(args, workspace, buildDir);

        File expectedDbpath = new File(workspace, "data/db");
        assertEquals(expectedDbpath, actualDbpath);
        assertEquals(format("--fork --logpath %s/mongodb.log --dbpath %s --port 1234",
                buildDir.getAbsolutePath(),
                expectedDbpath.getAbsolutePath()),
            args.toStringWithQuote());
    }

}

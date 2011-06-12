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

    @Before
    public void init() {
        workspace = tempFolder.newFolder("workspace");
    }

    @Test
    public void setupCmd_with_defaults() {

        ArgumentListBuilder args = new ArgumentListBuilder();
        File actualDbpath = new MongoBuildWrapper("mongo", null, null)
            .setupCmd(args, workspace);

        File expectedDbpath = new File(workspace, "data/db");
        assertEquals(expectedDbpath, actualDbpath);
        assertEquals(format("--fork --logpath %s/mongodb.log --dbpath %s",
                workspace.getAbsolutePath(),
                expectedDbpath.getAbsolutePath()),
            args.toStringWithQuote());
    }

    @Test
    public void setupCmd_with_dbpath() {

        ArgumentListBuilder args = new ArgumentListBuilder();
        File actualDbpath = new MongoBuildWrapper("mongo", "data_dir", null)
            .setupCmd(args, workspace);

        File expectedDbpath = new File(workspace, "data_dir");
        assertEquals(expectedDbpath, actualDbpath);
        assertEquals(format("--fork --logpath %s/mongodb.log --dbpath %s",
                workspace.getAbsolutePath(),
                expectedDbpath.getAbsolutePath()),
            args.toStringWithQuote());
    }

    @Test
    public void setupCmd_with_abusolute_dbpath() {

        ArgumentListBuilder args = new ArgumentListBuilder();

        String absolutePath = new File(tempFolder.getRoot(), "foo/bar/data_dir").getAbsolutePath();
        File actualDbpath = new MongoBuildWrapper("mongo", absolutePath, null)
            .setupCmd(args, workspace);

        assertEquals(new File(absolutePath), actualDbpath);
        assertEquals(format("--fork --logpath %s/mongodb.log --dbpath %s",
                workspace.getAbsolutePath(),
                absolutePath),
            args.toStringWithQuote());
    }

    @Test
    public void setupCmd_with_port() {

        ArgumentListBuilder args = new ArgumentListBuilder();
        File actualDbpath = new MongoBuildWrapper("mongo", null, "1234")
            .setupCmd(args, workspace);

        File expectedDbpath = new File(workspace, "data/db");
        assertEquals(expectedDbpath, actualDbpath);
        assertEquals(format("--fork --logpath %s/mongodb.log --dbpath %s --port 1234",
                workspace.getAbsolutePath(),
                expectedDbpath.getAbsolutePath()),
            args.toStringWithQuote());
    }

}

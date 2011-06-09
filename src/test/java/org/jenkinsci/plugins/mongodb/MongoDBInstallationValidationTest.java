package org.jenkinsci.plugins.mongodb;

import static org.jenkinsci.plugins.mongodb.MongoDBInstallation.DescriptorImpl.doCheckHome;
import static org.junit.Assert.assertEquals;
import static org.jenkinsci.plugins.mongodb.Messages.*;
import hudson.util.FormValidation;
import hudson.util.FormValidation.Kind;

import java.io.File;
import java.io.IOException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class MongoDBInstallationValidationTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void doCheckHome_Empty() {
        assertEquals(Kind.OK, doCheckHome(new File("")).kind);
    }

    @Test
    public void doCheckHome_File() throws IOException {
        FormValidation actual = doCheckHome(tempFolder.newFile("file"));
        assertEquals(Kind.ERROR, actual.kind);
        assertEquals(MongoDB_NotDirectory(), actual.getMessage());
    }

    @Test
    public void doCheckHome_Not_MongoDB_Home() {
        File value = tempFolder.newFolder("folder");
        FormValidation actual = doCheckHome(value);
        assertEquals(Kind.ERROR, actual.kind);
        assertEquals(MongoDB_NotMongoDBDirectory(value), actual.getMessage());
    }

    @Test
    public void doCheckHome_Valid_MongoDB_Home() throws IOException {
        File value = tempFolder.newFolder("folder");
        new File(value, "bin").mkdir();
        new File(value, "bin/mongod").createNewFile();

        FormValidation actual = doCheckHome(value);
        assertEquals(Kind.OK, actual.kind);
    }
}

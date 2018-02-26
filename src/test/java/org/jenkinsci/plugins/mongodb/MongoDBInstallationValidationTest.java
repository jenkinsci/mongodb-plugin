package org.jenkinsci.plugins.mongodb;

import static org.junit.Assert.assertEquals;
import static org.jenkinsci.plugins.mongodb.Messages.*;
import hudson.util.FormValidation;
import hudson.util.FormValidation.Kind;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

public class MongoDBInstallationValidationTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();
    
    private MongoDBInstallation.DescriptorImpl descr = new MongoDBInstallation.DescriptorImpl();

    @Test
    public void doCheckHome_Empty() {
        assertEquals(Kind.OK, descr.doCheckHome(new File("")).kind);
    }

    @Test
    public void doCheckHome_File() throws IOException {
        FormValidation actual = descr.doCheckHome(tempFolder.newFile("file"));
        assertEquals(Kind.ERROR, actual.kind);
        assertEquals(MongoDB_NotDirectory(), actual.getMessage());
    }

    @Test
    public void doCheckHome_Not_MongoDB_Home() throws IOException {
        File value = tempFolder.newFolder("folder");
        FormValidation actual = descr.doCheckHome(value);
        assertEquals(Kind.ERROR, actual.kind);
        assertEquals(MongoDB_NotMongoDBDirectory(value), actual.getMessage());
    }

    @Test
    public void doCheckHome_Valid_MongoDB_Home() throws IOException {
        File value = tempFolder.newFolder("folder");
        new File(value, "bin").mkdir();
        new File(value, "bin/mongod").createNewFile();

        FormValidation actual = descr.doCheckHome(value);
        assertEquals(Kind.OK, actual.kind);
    }
    
    @RunWith(Parameterized.class)
	public static class CheckStartTimeout {
		
		private String inputValue;
		private Kind expectedKind;
		private String expectedMessage;
		
		public CheckStartTimeout(String inputValue, Kind expectedKind, String expectedMessage) {
			this.inputValue = inputValue;
			this.expectedKind = expectedKind;
			this.expectedMessage = expectedMessage;
		}
		
		@Test
		public void test() {
			FormValidation actual = MongoDBInstallation.DescriptorImpl.doCheckStartTimeout(inputValue);
			assertEquals(expectedKind, actual.kind);
			assertEquals(expectedMessage, actual.getMessage());
		}
		
		@Parameters
		public static Collection<Object[]> data() {
			return Arrays.asList(
					ok(""),
					ok("27017"),
					ok("65535"),
                    ok("0"),
					error(MongoDB_InvalidStartTimeout(), "a"),
					error(MongoDB_InvalidStartTimeout(), "-1"),					
					error(MongoDB_InvalidStartTimeout(), "100.0"),
					error(MongoDB_InvalidStartTimeout(), "100,0")
					);
		}
	}
    
    private static Object[] ok(Object... params) {
        return toParams(Kind.OK, null, params);
    }

    private static Object[] error(String message, Object... params) {
        return toParams(Kind.ERROR, message, params);
    }

    private static Object[] toParams(Kind kind, String message, Object... params) {
        List<Object> list = new ArrayList<Object>(Arrays.asList(params));
        list.add(kind);
        list.add(message);
        return list.toArray(new Object[params.length + 2]);
    }
}

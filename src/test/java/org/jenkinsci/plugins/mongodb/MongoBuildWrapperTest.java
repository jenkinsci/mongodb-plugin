package org.jenkinsci.plugins.mongodb;

import static java.lang.String.format;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import hudson.FilePath;
import hudson.Launcher;
import hudson.remoting.Callable;
import hudson.remoting.VirtualChannel;
import hudson.util.ArgumentListBuilder;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.For;
import org.mockito.Mockito;

@SuppressWarnings({ "unchecked", "rawtypes" })
@For(MongoBuildWrapper.class)
public class MongoBuildWrapperTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private FilePath workspace;

	private Launcher mockLauncher;

	private VirtualChannel mockChannel;

    @Before
    public void init() {
        workspace = new FilePath(tempFolder.newFolder("workspace"));
        mockLauncher = mock(Launcher.class);
        mockChannel = mock(VirtualChannel.class);
        when(mockLauncher.getChannel()).thenReturn(mockChannel);
    }

    @Test
    public void setupCmd_with_defaults() throws Exception {

        ArgumentListBuilder args = new ArgumentListBuilder();

        FilePath actualDbpath = new MongoBuildWrapper("mongo", null, null, null, 0)
            .setupCmd(mockLauncher,args, workspace, true,null);
        
        FilePath expectedDbpath = workspace.child("data").child("db");
        
        assertEquals(expectedDbpath, actualDbpath);
        assertEquals(format("--fork --logpath %s --dbpath %s",
        		workspace.child("mongodb.log").getRemote(),
                expectedDbpath.getRemote()),
            args.toStringWithQuote());
    }

    
	@Test
    public void setupCmd_with_dbpath() throws Throwable {

        ArgumentListBuilder args = new ArgumentListBuilder();
        when(mockChannel.call((Callable) Mockito.any())).thenReturn(Boolean.FALSE);
        
        FilePath actualDbpath = new MongoBuildWrapper("mongo", "data_dir", null,null,0)
            .setupCmd(mockLauncher,args, workspace, true,null);

        FilePath expectedDbpath = workspace.child("data_dir");
        FilePath expectedLogpath = workspace.child("mongodb.log");
        assertEquals(expectedDbpath, actualDbpath);
        assertEquals(format("--fork --logpath %s --dbpath %s",
        		expectedLogpath.getRemote(),
                expectedDbpath.getRemote()),
            args.toStringWithQuote());
    }

    @Test
    public void setupCmd_with_abusolute_dbpath() throws Throwable {

        ArgumentListBuilder args = new ArgumentListBuilder();
        when(mockChannel.call((Callable) Mockito.any())).thenReturn(Boolean.TRUE);
        
        FilePath absolutePath = new FilePath(tempFolder.getRoot()).child("foo").child("bar").child("data_dir");
        FilePath actualDbpath = new MongoBuildWrapper("mongo", absolutePath.getRemote(), null,null,0)
            .setupCmd(mockLauncher,args, workspace, true,null);

        assertEquals(absolutePath.getRemote(), actualDbpath.getRemote());
        
        assertEquals(format("--fork --logpath %s --dbpath %s",
        		workspace.child("mongodb.log").getRemote(),
        		absolutePath.getRemote()),
            args.toStringWithQuote());
    }

    @Test
    public void setupCmd_with_port() throws Exception {

        ArgumentListBuilder args = new ArgumentListBuilder();
        FilePath actualDbpath = new MongoBuildWrapper("mongo", null, "1234", null,0).setupCmd(mockLauncher,args, workspace, true,null);

        FilePath expectedDbpath = workspace.child("data").child("db");
        assertEquals(expectedDbpath.getRemote(), actualDbpath.getRemote());
        
        assertEquals(format("--fork --logpath %s --dbpath %s --port 1234",
        		workspace.child("mongodb.log").getRemote(),
                expectedDbpath.getRemote()),
            args.toStringWithQuote());
    }

    @Test
    public void setupCmd_without_fork() throws Exception{

    	ArgumentListBuilder args = new ArgumentListBuilder();
        FilePath actualDbpath = new MongoBuildWrapper("mongo", null, "1234", null,0).setupCmd(mockLauncher,args, workspace, false,null);

        FilePath expectedDbpath = workspace.child("data").child("db");
        assertEquals(expectedDbpath.getRemote(), actualDbpath.getRemote());
        
        assertEquals(format("--logpath %s --dbpath %s --port 1234",
        		workspace.child("mongodb.log").getRemote(),
                expectedDbpath.getRemote()),
            args.toStringWithQuote());
    }
    
    @Test
    public void setupCmd_with_parameters() throws Throwable {

        ArgumentListBuilder args = new ArgumentListBuilder();
        when(mockChannel.call((Callable) Mockito.any())).thenReturn(Boolean.FALSE);
        
        FilePath actualDbpath = new MongoBuildWrapper("mongo", "data_dir", null,"--smallfiles --syncdelay 0",0)
            .setupCmd(mockLauncher,args, workspace, true,null);

        FilePath expectedDbpath = workspace.child("data_dir");
        FilePath expectedLogpath = workspace.child("mongodb.log");
        assertEquals(expectedDbpath, actualDbpath);
        assertEquals(format("--fork --logpath %s --dbpath %s --smallfiles --syncdelay 0",
        		expectedLogpath.getRemote(),
                expectedDbpath.getRemote()),
            args.toStringWithQuote());
    }
    
    @Test
    public void setupCmd_with_parametersAndWhitespaces() throws Throwable {

        ArgumentListBuilder args = new ArgumentListBuilder();
        when(mockChannel.call((Callable) Mockito.any())).thenReturn(Boolean.FALSE);
        
        FilePath actualDbpath = new MongoBuildWrapper("mongo", "data_dir", null,"    --smallfiles       --syncdelay       0      ",0)
            .setupCmd(mockLauncher,args, workspace, true,null);

        FilePath expectedDbpath = workspace.child("data_dir");
        FilePath expectedLogpath = workspace.child("mongodb.log");
        assertEquals(expectedDbpath, actualDbpath);
        assertEquals(format("--fork --logpath %s --dbpath %s --smallfiles --syncdelay 0",
        		expectedLogpath.getRemote(),
                expectedDbpath.getRemote()),
            args.toStringWithQuote());
    }
    
    @Test
    public void setupCmd_with_parametersAndQuotes() throws Throwable {
    	
    	ArgumentListBuilder args = new ArgumentListBuilder();
    	when(mockChannel.call((Callable) Mockito.any())).thenReturn(Boolean.FALSE);
    	
    	FilePath actualDbpath = new MongoBuildWrapper("mongo", "data_dir", null,"--smallfiles  --keyFile c:\\Program Files\\Secure\\somekeyfile",0)
    	.setupCmd(mockLauncher,args, workspace, true,null);
    	
    	FilePath expectedDbpath = workspace.child("data_dir");
    	FilePath expectedLogpath = workspace.child("mongodb.log");
    	assertEquals(expectedDbpath, actualDbpath);
    	assertEquals(format("--fork --logpath %s --dbpath %s --smallfiles --keyFile \"c:\\Program Files\\Secure\\somekeyfile\"",
    			expectedLogpath.getRemote(),
    			expectedDbpath.getRemote()),
    			args.toStringWithQuote());
    }

}

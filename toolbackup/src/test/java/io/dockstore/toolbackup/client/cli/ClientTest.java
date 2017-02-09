package io.dockstore.toolbackup.client.cli;

import io.dockstore.toolbackup.client.cli.common.AWSConfig;
import io.dockstore.toolbackup.client.cli.common.DirCleaner;
import io.swagger.client.model.Tool;
import io.swagger.client.model.ToolVersion;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.dockstore.toolbackup.client.cli.constants.TestConstants.DIR;
import static io.dockstore.toolbackup.client.cli.constants.TestConstants.DIR_CHECK_SIZE;
import static io.dockstore.toolbackup.client.cli.constants.TestConstants.ID;
import static io.dockstore.toolbackup.client.cli.constants.TestConstants.IMG;
import static io.dockstore.toolbackup.client.cli.constants.TestConstants.TAG;
import static io.dockstore.toolbackup.client.cli.constants.TestConstants.TIME;
import static io.dockstore.toolbackup.client.cli.constants.TestConstants.TOOL_NAME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * Created by kcao on 25/01/17.
 */
public class ClientTest {
    private final ByteArrayOutputStream outputContent = new ByteArrayOutputStream();

    @BeforeClass
    public static void setUp() {
        AWSConfig.generateCredentials();
    }

    /**
     * Test for GA4GH API connection
     * @throws Exception
     */
    @Test
    public void setupClientEnvironment() throws Exception {
        OptionParser parser = new OptionParser();
        final OptionSet parsed = parser.parse("");
        Client client = new Client(parsed);
        client.setupClientEnvironment();
        assertTrue("client API could not start", client.getContainersApi() != null);
    }

    /**
     * Test that the calculation for files' sizes is correct
     * @throws Exception
     */
    @Test
    public void getFilesTotalSize_B() throws Exception {
        OptionParser parser = new OptionParser();
        final OptionSet parsed = parser.parse("");
        Client client = new Client(parsed);
        File dir = new File(DIR_CHECK_SIZE);
        long directorySize = client.getFilesTotalSizeB((List<File>) FileUtils.listFiles(dir, TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE));
        assertEquals(FileUtils.sizeOfDirectory(dir), directorySize);
    }

    private List<Tool> setUpTools() {
        List<Tool> tools = new ArrayList<>();
        Tool tool = new Tool();

        tool.setToolname(TOOL_NAME);

        tool.setId(ID);

        List<ToolVersion> toolVersions = new ArrayList<>();
        ToolVersion toolVersion = new ToolVersion();

        toolVersion.setId(ID + ":" + TAG);
        toolVersion.setMetaVersion(TIME);
        toolVersion.setImage(IMG);
        toolVersions.add(toolVersion);

        tool.setVersions(toolVersions);
        tools.add(tool);

        return tools;
    }

    private void setUpMap(int offset, DockerCommunicator dockerCommunicator) {
        Map<String, List<VersionDetail>> toolsToVersions = new HashMap<>();
        List<VersionDetail> versionsDetails = new ArrayList<>();
        versionsDetails.add(new VersionDetail(TAG, TIME, dockerCommunicator.getImageSize(IMG) + offset, 0, TIME, true, DIR));
        toolsToVersions.put(TOOL_NAME, versionsDetails);
        ReportGenerator.generateJSONMap(toolsToVersions, DIR);
    }

    @Test
    public void saveToLocal_newImage() throws Exception {
        OptionParser parser = new OptionParser();
        final OptionSet parsed = parser.parse("");
        Client client = new Client(parsed);

        DockerCommunicator dockerCommunicator = new DockerCommunicator();
        client.saveToLocal(DIR, DIR, setUpTools(), dockerCommunicator);
        assertTrue(new File(DIR + File.separator + ID + File.separator + TAG + ".tar").isFile());
    }

    @Test
    public void saveToLocal_newVersion() throws Exception {
        OptionParser parser = new OptionParser();
        final OptionSet parsed = parser.parse("");
        Client client = new Client(parsed);

        DockerCommunicator dockerCommunicator = new DockerCommunicator();
        setUpMap(1, dockerCommunicator);

        System.setOut(new PrintStream(outputContent));

        client.saveToLocal(DIR, DIR, setUpTools(), dockerCommunicator);
        assertTrue(outputContent.toString().contains("had changed"));
    }

    @Test
    public void saveToLocal_noChange() throws Exception {
        OptionParser parser = new OptionParser();
        final OptionSet parsed = parser.parse("");
        Client client = new Client(parsed);

        DockerCommunicator dockerCommunicator = new DockerCommunicator();
        setUpMap(0, dockerCommunicator);

        System.setOut(new PrintStream(outputContent));

        client.saveToLocal(DIR, DIR, setUpTools(), dockerCommunicator);
        assertTrue(outputContent.toString().contains("did not change"));
    }

    /**
     * Test for saving pulled docker image
     * @throws Exception
     */
    @Test
    public void saveDockerImage() throws Exception {
        OptionParser parser = new OptionParser();
        final OptionSet parsed = parser.parse("");
        Client client = new Client(parsed);
        DockerCommunicator dockerCommunicator = new DockerCommunicator();

        // confirm image can be pulled before test start
        assumeTrue(dockerCommunicator.pullDockerImage(IMG));

        File imgFile = new File( DIR + File.separator + "dockstore-saver-img.tar");
        client.saveDockerImage(IMG, imgFile, dockerCommunicator);
        assertTrue(imgFile.isFile());

        dockerCommunicator.closeDocker();
    }

    @AfterClass
    public static void closeDocker() {
        DirCleaner.deleteDir(DIR);
    }
}
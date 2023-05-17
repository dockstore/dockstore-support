package io.dockstore.toolbackup.client.cli;

import static io.dockstore.toolbackup.client.cli.constants.TestConstants.ID;
import static io.dockstore.toolbackup.client.cli.constants.TestConstants.TAG;
import static io.dockstore.toolbackup.client.cli.constants.TestConstants.TIME;
import static io.dockstore.toolbackup.client.cli.constants.TestConstants.TOOL_NAME;
import static io.dockstore.toolbackup.client.cli.constants.TestConstants.dir;
import static io.dockstore.toolbackup.client.cli.constants.TestConstants.dirCheckSize;
import static io.dockstore.toolbackup.client.cli.constants.TestConstants.img;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import io.dockstore.toolbackup.client.cli.common.AWSConfig;
import io.dockstore.toolbackup.client.cli.common.DirCleaner;
import io.swagger.client.model.Tool;
import io.swagger.client.model.ToolVersion;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Created by kcao on 25/01/17.
 */
public class ClientTest {

    private static Client client;
    private final ByteArrayOutputStream outputContent = new ByteArrayOutputStream();

    @BeforeClass
    public static void setUp() {
        OptionParser parser = new OptionParser();
        final OptionSet parsed = parser.parse("");
        client = new Client(parsed);
        AWSConfig.generateCredentials();
    }

    /**
     * Test for GA4GH API connection
     *
     */
    @Test
    public void setupClientEnvironment() {
        client.setupClientEnvironment();
        assertNotNull("client API could not start", client.getContainersApi());
    }

    /**
     * Test that the calculation for files' sizes is correct
     *
     */
    @Test
    public void getFilesTotalSizeB() {
        File newFile = new File(dirCheckSize + File.separator + "helloworld.txt");
        try {
            FileUtils.writeStringToFile(newFile, "Hello world!", "UTF-8");
        } catch (IOException e) {
            throw new RuntimeException("Could not create " + newFile.getAbsolutePath());
        }
        File dir = new File(dirCheckSize);
        long directorySize = client.getFilesTotalSizeB((List<File>) FileUtils.listFiles(dir, TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE));
        assertEquals(FileUtils.sizeOfDirectory(dir), directorySize);
        DirCleaner.deleteDir(dirCheckSize);
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
        toolVersion.setImage(img);
        toolVersions.add(toolVersion);

        tool.setVersions(toolVersions);
        tools.add(tool);

        return tools;
    }

    private void setUpMap(int offset, DockerCommunicator dockerCommunicator) {
        Map<String, List<VersionDetail>> toolsToVersions = new HashMap<>();
        List<VersionDetail> versionsDetails = new ArrayList<>();
        versionsDetails.add(new VersionDetail(TAG, TIME, dockerCommunicator.getImageSize(img) + offset, 0, TIME, true, dir));
        toolsToVersions.put(TOOL_NAME, versionsDetails);
        ReportGenerator.generateJSONMap(toolsToVersions, dir);
    }

    @Test
    public void saveToLocalNewImage() {
        DockerCommunicator dockerCommunicator = new DockerCommunicator();
        client.saveToLocal(dir, dir, setUpTools(), dockerCommunicator);
        assertTrue(new File(dir + File.separator + ID + File.separator + TAG + ".tar").isFile());
    }

    @Test
    public void saveToLocalNewVersion() {
        DockerCommunicator dockerCommunicator = new DockerCommunicator();
        setUpMap(1, dockerCommunicator);

        System.setOut(new PrintStream(outputContent));

        client.saveToLocal(dir, dir, setUpTools(), dockerCommunicator);
        assertTrue(outputContent.toString().contains("had changed"));
    }

    @Test
    public void saveToLocalNoChange() {
        DockerCommunicator dockerCommunicator = new DockerCommunicator();

        dockerCommunicator.pullDockerImage(img);
        setUpMap(0, dockerCommunicator);

        System.setOut(new PrintStream(outputContent));

        client.saveToLocal(dir, dir, setUpTools(), dockerCommunicator);
        assertTrue(outputContent.toString().contains("did not change"));
    }

    /**
     * Test for saving pulled docker image
     *
     */
    @Test
    public void saveDockerImage() {
        DockerCommunicator dockerCommunicator = new DockerCommunicator();

        // confirm image can be pulled before test start
        assumeTrue(dockerCommunicator.pullDockerImage(img));

        File imgFile = new File(dir + File.separator + "dockstore-saver-img.tar");
        client.saveDockerImage(img, imgFile, dockerCommunicator);
        assertTrue(imgFile.isFile());

        dockerCommunicator.closeDocker();
    }

    @AfterClass
    public static void closeDocker() {
        DirCleaner.deleteDir(dir);
    }
}

package io.dockstore.toolbackup.client.cli;

import static com.github.stefanbirkner.systemlambda.SystemLambda.catchSystemExit;
import static io.dockstore.toolbackup.client.cli.Client.CLIENT_ERROR;
import static io.dockstore.toolbackup.client.cli.constants.TestConstants.DIR;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import io.dockstore.toolbackup.client.cli.common.DirCleaner;
import java.io.File;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Created by kcao on 25/01/17.
 */
public class DirectoryGeneratorTest {

    @BeforeClass
    public static void setUp() {
        DirectoryGenerator.createDir(DIR);
    }

    /**
     * Test that the script exits if the user is attempting to create a directory with the same path as an existing file
     * @throws Exception
     */
    @Test
    public void createDirExistingFile() throws Exception {
        int statusCode = catchSystemExit(() -> {
            File file = new File(DIR + File.separator + "sameName.txt");
            assumeTrue(file.isFile() || !file.exists());
            file.createNewFile();
            DirectoryGenerator.createDir(file.getAbsolutePath());
        });
        assertEquals(CLIENT_ERROR, statusCode);
    }

    @AfterClass
    public static void shutDown() {
        DirCleaner.deleteDir(DIR);
    }
}

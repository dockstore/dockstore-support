package io.dockstore.toolbackup.client.cli;

import io.dockstore.toolbackup.client.cli.common.DirCleaner;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;

import java.io.File;

import static io.dockstore.toolbackup.client.cli.constants.TestConstants.DIR;
import static org.junit.Assume.assumeTrue;

/**
 * Created by kcao on 25/01/17.
 */
public class DirectoryGeneratorTest {
    @Rule
    public final ExpectedSystemExit exit = ExpectedSystemExit.none();

    @BeforeClass
    public static void setUp() {
        DirectoryGenerator.createDir(DIR);
    }

    /**
     * Test that the script exits if the user is attempting to create a directory with the same path as an existing file
     * @throws Exception
     */
    @Test
    public void createDir_existingFile() throws Exception {
        exit.expectSystemExit();
        File file = new File(DIR + File.separator + "sameName.txt");
        assumeTrue(file.isFile() || !file.exists());
        file.createNewFile();
        DirectoryGenerator.createDir(file.getAbsolutePath());
    }

    @AfterClass
    public static void shutDown() {
        DirCleaner.deleteDir(DIR);
    }
}
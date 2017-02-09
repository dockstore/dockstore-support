package io.dockstore.toolbackup.client.cli;

import io.dockstore.toolbackup.client.cli.common.DirCleaner;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.security.Permission;

import static io.dockstore.toolbackup.client.cli.constants.TestConstants.DIR;
import static io.dockstore.toolbackup.client.cli.constants.TestConstants.DIR_SAME_NAME;
import static org.junit.Assume.assumeTrue;

/**
 * Created by kcao on 25/01/17.
 */
public class DirectoryGeneratorTest {
    private static final SecurityManager SM = System.getSecurityManager();

    @BeforeClass
    public static void setUp() {
        System.setSecurityManager(new SecurityManager() {
            @Override
            public void checkExit(int status) {
                super.checkExit(status);
                throw new SecurityException("Overriding shutdown...");
            }

            @Override
            public void checkPermission(final Permission perm) {}
        });
    }

    /**
     * Test that the script exits if the user is attempting to create a directory with the same path as an existing file
     * @throws Exception
     */
    @Test(expected = SecurityException.class)
    public void createDir_existingFile() throws Exception {
        File file = new File(DIR_SAME_NAME);
        assumeTrue(file.isFile() || !file.exists());
        file.createNewFile();
        DirectoryGenerator.createDir(file.getAbsolutePath());
    }

    /**
     * Sanity check for creating a directory
     * @throws Exception
     */
    @Test
    public void createDir() throws Exception {
        DirectoryGenerator.createDir(DIR);
        DirCleaner.deleteDir(DIR);
    }

    @AfterClass
    public static void shutDown() {
        System.setSecurityManager(SM);
    }
}
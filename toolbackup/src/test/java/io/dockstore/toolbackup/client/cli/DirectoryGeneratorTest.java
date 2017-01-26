package io.dockstore.toolbackup.client.cli;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.security.Permission;

import static org.junit.Assume.assumeTrue;

/**
 * Created by kcao on 25/01/17.
 */
public class DirectoryGeneratorTest extends Base {
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

    @Test(expected = SecurityException.class)
    public void validatePath_existingFile() throws Exception {
        File file = new File(DIR + File.separator + "Same");
        assumeTrue(file.isFile() || !file.exists());
        file.createNewFile();
        DirectoryGenerator.validatePath(file.getAbsolutePath());
    }

    @Test
    public void validatePath_existingDir() throws Exception {
        DirectoryGenerator.validatePath(DIR);
    }

    @AfterClass
    public static void shutDown() {
        System.setSecurityManager(SM);
    }
}
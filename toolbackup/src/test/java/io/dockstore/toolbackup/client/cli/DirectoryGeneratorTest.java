package io.dockstore.toolbackup.client.cli;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;

import static io.dockstore.toolbackup.client.cli.Client.API_ERROR;

/**
 * Created by kcao on 25/01/17.
 */
public class DirectoryGeneratorTest {
    private static HierarchicalINIConfiguration config;
    protected static String NOT_YET;
    protected static String EXISTING_FILE;

    @BeforeClass
    public static void loadConfig() {
        String userHome = System.getProperty("user.home");
        try {
            File configFile = new File(userHome + File.separator + ".toolbackup" + File.separator + "config.ini");
            config = new HierarchicalINIConfiguration(configFile);
            NOT_YET = config.getString("dirgeneration.notyet");
            EXISTING_FILE = config.getString("dirgeneration.existingfile");
        } catch (ConfigurationException e) {
            ErrorExit.exceptionMessage(e, "", API_ERROR);
        }
    }

    @Test
    public void validatePath_notYetDir() throws Exception {
        DirectoryGenerator.validatePath(NOT_YET);
    }

    @Test
    public void validatePath_existingFile() throws Exception {
    }

    @Test
    public void validatePath_existingDir() throws Exception {
    }
}
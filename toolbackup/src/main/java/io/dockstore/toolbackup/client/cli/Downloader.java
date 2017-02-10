package io.dockstore.toolbackup.client.cli;

import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalINIConfiguration;

import java.io.File;
import java.nio.file.Paths;
import java.time.LocalDateTime;

import static io.dockstore.toolbackup.client.cli.Client.API_ERROR;
import static java.lang.System.out;

/**
 * Created by kcao on 20/01/17.
 */
class Downloader {
    private static HierarchicalINIConfiguration config;
    private final OptionSet options;
    private static String endpoint;
    private static final LocalDateTime TIME_NOW = LocalDateTime.now();
    private static String stringTime;

    Downloader(OptionSet options) {
        this.options = options;
    }

    static {
        stringTime = FormattedTimeGenerator.getFormattedTimeNow(TIME_NOW);
    }

    public static void main(String[] argv) {
        out.println("Downloader script started: " + stringTime);

        OptionParser parser = new OptionParser();
        final ArgumentAcceptingOptionSpec<String> bucketName = parser.accepts("bucket-name", "bucket to which files will be backed-up").withRequiredArg().defaultsTo("");
        final ArgumentAcceptingOptionSpec<String> keyPrefix = parser.accepts("key-prefix", "key prefix of bucket (ex. client)").withRequiredArg().defaultsTo("");
        final ArgumentAcceptingOptionSpec<String> localDir = parser.accepts("destination-dir", "local directory to which all specified cloud files will be downloaded").withRequiredArg().defaultsTo(".").ofType(String.class);

        final OptionSet options = parser.parse(argv);

        String local = options.valueOf(localDir);
        String dirPath = Paths.get(local).toAbsolutePath().toString();
        DirectoryGenerator.createDir(dirPath);

        setUpEndpoint();
        Downloader downloader = new Downloader(options);
        S3Communicator s3Communicator= new S3Communicator("dockstore", endpoint);
        downloader.download(options.valueOf(bucketName), options.valueOf(keyPrefix), dirPath, s3Communicator);

        final LocalDateTime end = LocalDateTime.now();
        FormattedTimeGenerator.elapsedTime(TIME_NOW, end);
    }

    public void download(String bucketName, String keyPrefix, String dirPath, S3Communicator s3Communicator) {
        s3Communicator.downloadDirectory(bucketName, keyPrefix, dirPath);
        s3Communicator.shutDown();
    }

    private static void setUpEndpoint() {
        String userHome = System.getProperty("user.home");
        try {
            File configFile = new File(userHome + File.separator + ".toolbackup" + File.separator + "config.ini");
            config = new HierarchicalINIConfiguration(configFile);
        } catch (ConfigurationException e) {
            ErrorExit.exceptionMessage(e, "", API_ERROR);
        }

        // pull out the variables from the config
        endpoint = config.getString("endpoint");
    }
}

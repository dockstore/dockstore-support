package io.dockstore.toolbackup.client.cli;

import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.OptionParser;
import joptsimple.OptionSet;

import java.nio.file.Paths;

/**
 * Created by kcao on 20/01/17.
 */
public class Downloader {
    private final OptionSet options;
    private final S3Communicator s3Communicator= new S3Communicator();

    public Downloader(OptionSet options) {
        this.options = options;
    }

    public static void main(String[] argv) {
        OptionParser parser = new OptionParser();
        final ArgumentAcceptingOptionSpec<String> bucketName = parser.accepts("bucket-name", "bucket to which files will be backed-up").withRequiredArg().defaultsTo("");
        final ArgumentAcceptingOptionSpec<String> keyPrefix = parser.accepts("key-prefix", "key prefix of bucket (ex. client)").withRequiredArg().defaultsTo("");
        final ArgumentAcceptingOptionSpec<String> localDir = parser.accepts("destination-dir", "local directory to which all specified cloud files will be downloaded").withRequiredArg().defaultsTo(".").ofType(String.class);

        final OptionSet options = parser.parse(argv);

        String local = options.valueOf(localDir);
        String dirPath = Paths.get(local).toAbsolutePath().toString();
        DirectoryGenerator.validatePath(dirPath);

        Downloader downloader = new Downloader(options);
        downloader.download(options.valueOf(bucketName), options.valueOf(keyPrefix), dirPath);
    }

    private void download(String bucketName, String keyPrefix, String dirPath) {
        s3Communicator.downloadDirectory(bucketName, keyPrefix, dirPath);
        s3Communicator.shutDown();
    }
}

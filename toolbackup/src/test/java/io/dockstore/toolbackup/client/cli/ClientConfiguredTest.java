package io.dockstore.toolbackup.client.cli;

/**
 * Created by kcao on 19/01/17.
 */
public class ClientConfiguredTest {
    /*
    private static Client client;
    private static final String PATH = "/home/kcao/Documents/Projects/dockstore-saver/ClientConfigTest";
    private static final String BUCKET_NAME = "configuredbucket";
    private static final String KEY_PREFIX = "configuredprefix";

    @BeforeClass
    public static void initClient() {
        final OptionParser parser = new OptionParser();
        final ArgumentAcceptingOptionSpec<String> bucketName = parser.accepts("bucket-name", "bucket to which files will be backed-up").withRequiredArg().defaultsTo("");
        final ArgumentAcceptingOptionSpec<String> keyPrefix = parser.accepts("key-prefix", "key prefix of bucket (ex. client)").withRequiredArg().defaultsTo("");
        final ArgumentAcceptingOptionSpec<String> localDir = parser.accepts("local-dir", "local directory to which files will be backed-up").withRequiredArg().defaultsTo(".").ofType(String.class);
        final ArgumentAcceptingOptionSpec<Boolean> isTestMode = parser.accepts("test-mode-activate", "if true test mode is activated").withRequiredArg().ofType(Boolean.class);

        final OptionSet options = parser.parse(new String[] { "--local-dir", PATH, "--bucket-name", BUCKET_NAME, "--key-prefix", KEY_PREFIX, "--test-mode-activate", "true" });

        client = new Client(options);
    }
    @Test(expected = IllegalArgumentException.class)
    public void testFailUpload() {
        client.uploadToCloud("/home/kcao/nonexistent", BUCKET_NAME, new S3Communicator());
    }

    @Test
    public void testSuccessUpload() {
        final List<File> files = (List<File>) FileUtils.listFiles(new File(PATH), TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE);
        final List<String> filePaths = files.stream().map(file -> { return file.getAbsolutePath().replace(PATH, ""); } ).collect(Collectors.toList());

        S3Communicator s3Communicator = new S3Communicator();
        s3Communicator.uploadDirectory(BUCKET_NAME, PATH);
        final List<String> keyNames = s3Communicator.listKeys(BUCKET_NAME);
        s3Communicator.shutDown();

        out.println("filePaths: " + filePaths.toString());
        out.println("keyNames: " + keyNames.toString());

        assertEquals(new HashSet<String>(filePaths), new HashSet<String>(keyNames));
    }
    */
}

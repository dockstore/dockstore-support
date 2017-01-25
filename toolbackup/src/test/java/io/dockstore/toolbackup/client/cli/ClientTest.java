package io.dockstore.toolbackup.client.cli;

public class ClientTest {
    /*
    private static final String PATH = "/home/kcao/Documents/Projects/dockstore-saver/ClientTest";
    private static final List<File> HTML_FILES = (List<File>) FileUtils.listFiles(new File(PATH), new String[] {"html"}, true);
    private static final List<File> JSON_MAP = (List<File>) FileUtils.listFiles(new File(PATH), new String[] {"JSON"}, true);

    @Test
    public void setupEnvironment() throws Exception {
        OptionParser parser = new OptionParser();
        final OptionSet parsed = parser.parse("");
        Client client = new Client(parsed);
        client.setupClientEnvironment();
        assertTrue("client API could not start", client.getContainersApi() != null);
    }

    @Test
    public void testMain() throws IOException {
        S3Communicator s3Communicator = new S3Communicator();
        Client.main(new String[] { "--bucket-name", "testbucket", "key-prefix", "test", "--local-dir", PATH, "--test-mode-activate", "true" });
        validateHTMLSyntax();
        validateReport();
        s3Communicator.shutDown();
        validateJSONSyntax();
        validateJSONMap();
    }

    @Test
    public void validateHTMLSyntax() {
        Tidy tidy = new Tidy();

        for(File file : HTML_FILES) {
            try {
                tidy.parse(FileUtils.openInputStream(file), out);
            } catch (IOException e) {
                e.printStackTrace();
            }
            if(tidy.getParseErrors() > 0) {
                break;
            }
        }
        assertEquals(tidy.getParseErrors(), 0);
    }

    @Test
    public void validateReport() throws IOException {
        List<String> headings = new ArrayList<>();

        for(File file : HTML_FILES) {
            Document doc = Jsoup.parse(file, "UTF-8");
            Elements thHeadings = doc.select("th");

            for(Element heading : thHeadings) {
                headings.add(heading.text());
            }
        }

        assertEquals(headings.stream().anyMatch(str -> str.contains("Meta-Version") || str.contains("Version") || str.contains("Size") || str.contains("Time") || str.contains("Availability")), true);
    }

    @Test
    public void validateJSONSyntax() throws IOException, JsonSyntaxException {
        assertEquals(JSON_MAP.size(), 1);

        Gson gson = new Gson();

        for(File file : JSON_MAP) {
            gson.fromJson(FileUtils.readFileToString(file, "UTF-8"), Object.class);
        }
    }

    @Test
    public void validateJSONMap() throws IOException {
        assertEquals(JSON_MAP.size(), 1);
        ReportGenerator.loadJSONMap(JSON_MAP.get(0).getAbsolutePath());
    }
    */
}
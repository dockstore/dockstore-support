package io.dockstore.topicgenerator.client.cli;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import java.io.File;

public class TopicGeneratorCommandLineArgs {
    public static final String DEFAULT_CONFIG_FILE_NAME = "topic-generator.config";
    public static final String DEFAULT_ENTRIES_FILE_NAME = "entries.csv";

    @Parameter(names = "--help", description = "Prints help for topicgenerator", help = true)
    private boolean help = false;

    public boolean isHelp() {
        return help;
    }

    @Parameters(commandNames = { "generate-topics" }, commandDescription = "Generate topics for public Dockstore entries using the gpt-3.5-turbo-16k AI model")
    public static class GenerateTopicsCommand extends TopicGeneratorCommandLineArgs {
        @Parameter(names = {"-c", "--config"}, description = "The config file path.")
        private File config = new File("./" + DEFAULT_CONFIG_FILE_NAME);

        @Parameter(names = {"-e", "--entries"}, description = "The file path to the CSV file containing the TRS ID, and version name of the entries to generate topics for. The first line of the file should contain the CSV fields: trsID,version")
        private String entriesCsvFilePath = "./" + DEFAULT_ENTRIES_FILE_NAME;

        public File getConfig() {
            return config;
        }

        public String getEntriesCsvFilePath() {
            return entriesCsvFilePath;
        }
    }
}
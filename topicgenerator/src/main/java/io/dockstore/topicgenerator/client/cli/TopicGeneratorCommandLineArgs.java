package io.dockstore.topicgenerator.client.cli;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import io.dockstore.topicgenerator.helper.AIModelType;
import java.io.File;

public class TopicGeneratorCommandLineArgs {
    public static final String DEFAULT_CONFIG_FILE_NAME = "topic-generator.config";
    public static final String DEFAULT_ENTRIES_FILE_NAME = "entries.csv";

    @Parameter(names = "--help", description = "Prints help for topicgenerator", help = true)
    private boolean help = false;

    @Parameter(names = {"-c", "--config"}, description = "The config file path.")
    private File config = new File("./" + DEFAULT_CONFIG_FILE_NAME);

    public boolean isHelp() {
        return help;
    }

    public File getConfig() {
        return config;
    }

    @Parameters(commandNames = { "get-topic-candidates" }, commandDescription = "Create a CSV file containing AI topic candidates from Dockstore. Use the generate-topics command to generate topics for these candidates.")
    public static class GetTopicCandidates {
        @Parameter(names = {"-o", "--outputPath"}, description = "The output file path used for the CSV file created containing AI topic candidates from Dockstore.")
        private String entriesCsvOutputFilePath = DEFAULT_ENTRIES_FILE_NAME;

        public String getEntriesCsvOutputFilePath() {
            return entriesCsvOutputFilePath;
        }
    }

    @Parameters(commandNames = { "generate-topics" }, commandDescription = "Generate topics for public Dockstore entries using AI. Use the upload-topics command to upload these topics to Dockstore.")
    public static class GenerateTopicsCommand {

        @Parameter(names = {"-e", "--entries"}, description = "The file path to the CSV file containing the TRS ID, and version name of the entries to generate topics for. The first line of the file should contain the CSV fields: trsID,version")
        private String entriesCsvFilePath = "./" + DEFAULT_ENTRIES_FILE_NAME;

        @Parameter(names = {"-m", "--model"}, description = "The AI model to use")
        private AIModelType aiModelType = AIModelType.CLAUDE_3_HAIKU;

        public String getEntriesCsvFilePath() {
            return entriesCsvFilePath;
        }

        public AIModelType getAiModel() {
            return aiModelType;
        }

        /**
         * Headers for the input data file of entries to generate AI topics for.
         */
        public enum InputCsvHeaders {
            trsId, version
        }

        /**
         * Headers for the output file containing the AI generated topics.
         */
        public enum OutputCsvHeaders {
            trsId,
            version,
            descriptorUrl, // Raw GitHub URL of the descriptor file used to generate the topic
            descriptorChecksum, // Checksum of the descriptor file used to generate the topic. Can be used to determine if the content has changed
            isTruncated, // Whether the descriptor file content truncated because it exceeded the token maximum
            promptTokens, // Number of tokens in prompt
            completionTokens, // Number of tokens in response
            cost, // Estimated cost of the prompt and completion tokens
            finishReason, // The reason that the response stopped
            aiTopic
        }
    }

    @Parameters(commandNames = { "upload-topics" }, commandDescription = "Upload AI topics, generated by the generate-topics command, for public Dockstore entries.")
    public static class UploadTopicsCommand {
        @Parameter(names = {"-ai", "--aiTopics"}, required = true, description = "The file path to the CSV file containing the TRS ID, and AI topics of the entries to upload topics for. The first line of the file should contain the CSV fields: trsId,aiTopic. The output file generated by the generate-topics command can be used as the argument.")
        private String aiTopicsCsvFilePath;

        public String getAiTopicsCsvFilePath() {
            return aiTopicsCsvFilePath;
        }
    }
}

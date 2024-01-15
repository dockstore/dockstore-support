package io.dockstore.topicgenerator.client.cli;

import static io.dockstore.utils.ConfigFileUtils.getConfiguration;
import static io.dockstore.utils.ExceptionHandler.GENERIC_ERROR;
import static io.dockstore.utils.ExceptionHandler.IO_ERROR;
import static io.dockstore.utils.ExceptionHandler.exceptionMessage;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.MissingCommandException;
import com.beust.jcommander.ParameterException;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingResult;
import com.knuddels.jtokkit.api.ModelType;
import com.theokanning.openai.completion.chat.ChatCompletionChoice;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.ApiException;
import io.dockstore.openapi.client.Configuration;
import io.dockstore.openapi.client.api.Ga4Ghv20Api;
import io.dockstore.openapi.client.model.FileWrapper;
import io.dockstore.openapi.client.model.ToolVersion;
import io.dockstore.topicgenerator.client.cli.TopicGeneratorCommandLineArgs.GenerateTopicsCommand;
import io.dockstore.topicgenerator.helper.OpenAIHelper;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TopicGeneratorClient {
    // Headers for the input CSV file
    public static final String TRS_ID_CSV_HEADER = "trsId";
    public static final String VERSION_CSV_HEADER = "version";
    // Headers for the output CSV file
    public static final String AI_TOPIC_CSV_HEADER = "aiTopic";
    public static final String PROMPT_TOKENS = "promptTokens"; // Number of tokens in prompt
    public static final String COMPLETION_TOKENS = "completionTokens"; // Number of tokens in response
    public static final String FINISH_REASON = "finishReason"; // The reason that the response stopped.
    public static final String IS_TRUNCATED = "isTruncated"; // Whether the descriptor file content truncated because it exceeded the token maximum
    public static final String OUTPUT_FILE_PREFIX = "generated-topics";
    protected static final String[] INPUT_CSV_HEADERS = { TRS_ID_CSV_HEADER, VERSION_CSV_HEADER };
    protected static final String[] OUTPUT_CSV_HEADERS = { TRS_ID_CSV_HEADER, VERSION_CSV_HEADER, IS_TRUNCATED, PROMPT_TOKENS, COMPLETION_TOKENS, FINISH_REASON, AI_TOPIC_CSV_HEADER };
    private static final Logger LOG = LoggerFactory.getLogger(TopicGeneratorClient.class);
    // Using GPT 3.5-turbo-16k because it's cheaper and faster than GPT4, and it takes more tokens (16k). GPT4 seems to cause a lot of timeouts.
    private static final ModelType AI_MODEL = ModelType.GPT_3_5_TURBO_16K;
    private static final EncodingRegistry REGISTRY = Encodings.newDefaultEncodingRegistry();
    private static final Encoding ENCODING = REGISTRY.getEncodingForModel(AI_MODEL);

    TopicGeneratorClient() {
    }

    public static void main(String[] args) {
        final TopicGeneratorCommandLineArgs commandLineArgs = new TopicGeneratorCommandLineArgs();
        final JCommander jCommander = new JCommander(commandLineArgs);
        final GenerateTopicsCommand generateTopicsCommand = new GenerateTopicsCommand();
        jCommander.addCommand(generateTopicsCommand);

        try {
            jCommander.parse(args);
        } catch (MissingCommandException e) {
            jCommander.usage();
            if (e.getUnknownCommand().isEmpty()) {
                LOG.error("No command entered");
            } else {
                LOG.error("Unknown command");
            }
            exceptionMessage(e, "The command is missing", GENERIC_ERROR);
        } catch (ParameterException e) {
            jCommander.usage();
            exceptionMessage(e, "Error parsing arguments", GENERIC_ERROR);
        }

        if (jCommander.getParsedCommand() == null || commandLineArgs.isHelp()) {
            jCommander.usage();
        } else if ("generate-topics".equals(jCommander.getParsedCommand())) {
            if (generateTopicsCommand.isHelp()) {
                jCommander.usage();
            } else {
                final INIConfiguration config = getConfiguration(generateTopicsCommand.getConfig());
                final TopicGeneratorConfig topicGeneratorConfig = new TopicGeneratorConfig(config);

                // Read CSV file
                Iterable<CSVRecord> entriesCsvRecords = null;
                try {
                    final Reader entriesCsv = new FileReader(generateTopicsCommand.getEntriesCsvFilePath());
                    CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
                            .setHeader(INPUT_CSV_HEADERS)
                            .setSkipHeaderRecord(true)
                            .setTrim(true)
                            .build();
                    entriesCsvRecords = csvFormat.parse(entriesCsv);
                } catch (IOException e) {
                    exceptionMessage(e, "Unable to read input CSV file", IO_ERROR);
                }

                final TopicGeneratorClient topicGeneratorClient = new TopicGeneratorClient();
                topicGeneratorClient.generateTopics(topicGeneratorConfig, entriesCsvRecords);
            }
        }
    }

    private ApiClient setupApiClient(String serverUrl) {
        ApiClient apiClient = Configuration.getDefaultApiClient();
        apiClient.setBasePath(serverUrl);
        return apiClient;
    }

    /**
     * Generates a topic for public entries by asking the GPT-3.5-turbo-16k AI model to summarize the content of the entry's primary descriptor.
     * @param topicGeneratorConfig
     * @param entriesCsvRecords
     */
    private void generateTopics(TopicGeneratorConfig topicGeneratorConfig, Iterable<CSVRecord> entriesCsvRecords) {
        final ApiClient apiClient = setupApiClient(topicGeneratorConfig.dockstoreServerUrl());
        final Ga4Ghv20Api ga4Ghv20Api = new Ga4Ghv20Api(apiClient);
        final OpenAiService openAiService = new OpenAiService(topicGeneratorConfig.openaiApiKey());
        final String outputFileName = OUTPUT_FILE_PREFIX + "_" + AI_MODEL + "_" + Instant.now().truncatedTo(ChronoUnit.SECONDS).toString().replace("-", "").replace(":", "") + ".csv";

        try (CSVPrinter csvPrinter = new CSVPrinter(new FileWriter(outputFileName, StandardCharsets.UTF_8), CSVFormat.DEFAULT.builder().setHeader(OUTPUT_CSV_HEADERS).build())) {
            for (CSVRecord entry: entriesCsvRecords) {
                final String trsId = entry.get(TRS_ID_CSV_HEADER);
                final String versionId = entry.get(VERSION_CSV_HEADER);

                // Get descriptor file content and entry type
                FileWrapper descriptorFile;
                String entryType;
                try {
                    entryType = ga4Ghv20Api.toolsIdGet(trsId).getToolclass().getName().toLowerCase();
                    final ToolVersion version = ga4Ghv20Api.toolsIdVersionsVersionIdGet(trsId, versionId);
                    final String descriptorType = version.getDescriptorType().get(0).toString();
                    descriptorFile = ga4Ghv20Api.toolsIdVersionsVersionIdTypeDescriptorGet(trsId, descriptorType, versionId);
                } catch (ApiException ex) {
                    LOG.error("Could not get entry with TRS ID {} and version {}, skipping", trsId, versionId, ex);
                    continue;
                }
                final String descriptorContent = descriptorFile.getContent();

                // Create ChatGPT request
                try {
                    getAiGeneratedTopicAndRecordToCsv(openAiService, csvPrinter, trsId, versionId, entryType, descriptorContent);
                    LOG.info("Generated topic for entry with TRS ID {} and version {}", trsId, versionId);
                } catch (Exception ex) {
                    LOG.error("Unable to generate topic for entry with TRS ID {} and version {}, skipping", trsId, versionId, ex);
                }
            }
        } catch (IOException e) {
            exceptionMessage(e, "Unable to create new CSV output file", IO_ERROR);
        }
    }

    /**
     * Generates a topic for the entry by asking the GPT-3.5-turbo-16k AI model to summarize the contents of the entry's primary descriptor.
     * Records the result in a CSV file.
     * @param openAiService
     * @param csvPrinter
     * @param trsId
     * @param versionId
     * @param entryType
     * @param descriptorContent
     */
    private void getAiGeneratedTopicAndRecordToCsv(OpenAiService openAiService, CSVPrinter csvPrinter, String trsId, String versionId, String entryType, String descriptorContent) {
        // A character limit is specified but ChatGPT doesn't follow it strictly
        final String systemPrompt = "You will be provided with a " + entryType + ", and your task is to summarize it in one sentence where the first word is a verb. Use a maximum of 150 characters.";
        final ChatMessage systemMessage = new ChatMessage(ChatMessageRole.SYSTEM.value(), systemPrompt);
        // The sum of the number of tokens in the request and response cannot exceed the model's maximum context length.
        final int maxResponseTokens = 100; // One token is roughly 4 characters. Using 100 tokens because setting it too low might truncate the response
        // Chat completion API calls include additional tokens for message-based formatting. Calculate how long the descriptor content can be and truncate if needed
        final int maxUserMessageTokens = OpenAIHelper.getMaximumAmountOfTokensForUserMessageContent(REGISTRY, AI_MODEL, systemMessage, maxResponseTokens);
        final EncodingResult encoded = ENCODING.encode(descriptorContent, maxUserMessageTokens); // Encodes the content up to the maximum number of tokens specified
        final String truncatedDescriptorContent = ENCODING.decode(encoded.getTokens()); // Decode the tokens to get the truncated content string

        final ChatMessage userMessage = new ChatMessage(ChatMessageRole.USER.value(), truncatedDescriptorContent);
        final List<ChatMessage> messages = List.of(systemMessage, userMessage);

        ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest
                .builder()
                .model(AI_MODEL.getName())
                .messages(messages)
                .n(1)
                .maxTokens(maxResponseTokens)
                .logitBias(new HashMap<>())
                .build();
        final ChatCompletionResult chatCompletionResult = openAiService.createChatCompletion(chatCompletionRequest);

        if (chatCompletionResult.getChoices().isEmpty()) {
            // I don't think this should happen, but check anyway
            LOG.error("There was no chat completion choices, skipping");
            return;
        }

        final ChatCompletionChoice chatCompletionChoice = chatCompletionResult.getChoices().get(0);
        final String aiGeneratedTopic = chatCompletionChoice.getMessage().getContent();
        final String finishReason = chatCompletionChoice.getFinishReason();
        final long promptTokens = chatCompletionResult.getUsage().getPromptTokens();
        final long completionTokens = chatCompletionResult.getUsage().getCompletionTokens();

        // Write response to new CSV file
        try {
            csvPrinter.printRecord(trsId, versionId, encoded.isTruncated(), promptTokens, completionTokens, finishReason, aiGeneratedTopic);
        } catch (IOException e) {
            LOG.error("Unable to write CSV record to file, skipping", e);
        }
    }
}

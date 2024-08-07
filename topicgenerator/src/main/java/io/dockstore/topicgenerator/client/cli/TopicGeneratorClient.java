package io.dockstore.topicgenerator.client.cli;

import static io.dockstore.utils.ConfigFileUtils.getConfiguration;
import static io.dockstore.utils.DockstoreApiClientUtils.setupApiClient;
import static io.dockstore.utils.ExceptionHandler.GENERIC_ERROR;
import static io.dockstore.utils.ExceptionHandler.IO_ERROR;
import static io.dockstore.utils.ExceptionHandler.exceptionMessage;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.MissingCommandException;
import com.beust.jcommander.ParameterException;
import com.google.common.collect.Lists;
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
import io.dockstore.common.NextflowUtilities;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.ApiException;
import io.dockstore.openapi.client.api.ExtendedGa4GhApi;
import io.dockstore.openapi.client.api.Ga4Ghv20Api;
import io.dockstore.openapi.client.model.FileWrapper;
import io.dockstore.openapi.client.model.ToolVersion;
import io.dockstore.openapi.client.model.ToolVersion.DescriptorTypeEnum;
import io.dockstore.openapi.client.model.UpdateAITopicRequest;
import io.dockstore.topicgenerator.client.cli.TopicGeneratorCommandLineArgs.GenerateTopicsCommand;
import io.dockstore.topicgenerator.client.cli.TopicGeneratorCommandLineArgs.GenerateTopicsCommand.OutputCsvHeaders;
import io.dockstore.topicgenerator.client.cli.TopicGeneratorCommandLineArgs.UploadTopicsCommand;
import io.dockstore.topicgenerator.helper.ChuckNorrisFilter;
import io.dockstore.topicgenerator.helper.OpenAIHelper;
import io.dockstore.topicgenerator.helper.StringFilter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TopicGeneratorClient {
    public static final String OUTPUT_FILE_PREFIX = "generated-topics";
    private static final Logger LOG = LoggerFactory.getLogger(TopicGeneratorClient.class);
    // Using GPT 3.5-turbo because it's cheaper and faster than GPT4, and it takes more tokens (16k). GPT4 seems to cause a lot of timeouts.
    // https://platform.openai.com/docs/models/gpt-3-5-turbo
    private static final ModelType AI_MODEL = ModelType.GPT_3_5_TURBO;
    private static final int MAX_CONTEXT_LENGTH = 16385;
    private static final EncodingRegistry REGISTRY = Encodings.newDefaultEncodingRegistry();
    private static final Encoding ENCODING = REGISTRY.getEncodingForModel(AI_MODEL);
    private final List<StringFilter> stringFilters = Lists.newArrayList(new ChuckNorrisFilter("en"), new ChuckNorrisFilter("fr-CA-u-sd-caqc"));

    TopicGeneratorClient() {
    }

    public static void main(String[] args) {
        final TopicGeneratorCommandLineArgs commandLineArgs = new TopicGeneratorCommandLineArgs();
        final JCommander jCommander = new JCommander(commandLineArgs);
        final GenerateTopicsCommand generateTopicsCommand = new GenerateTopicsCommand();
        final UploadTopicsCommand uploadTopicsCommand = new UploadTopicsCommand();
        jCommander.addCommand(generateTopicsCommand);
        jCommander.addCommand(uploadTopicsCommand);

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
        } else {
            final INIConfiguration config = getConfiguration(commandLineArgs.getConfig());
            final TopicGeneratorConfig topicGeneratorConfig = new TopicGeneratorConfig(config);
            final TopicGeneratorClient topicGeneratorClient = new TopicGeneratorClient();

            if ("generate-topics".equals(jCommander.getParsedCommand())) {
                // Read CSV file
                topicGeneratorClient.generateTopics(topicGeneratorConfig, generateTopicsCommand.getEntriesCsvFilePath());
            } else if ("upload-topics".equals(jCommander.getParsedCommand())) {
                // Read CSV file
                topicGeneratorClient.uploadTopics(topicGeneratorConfig, uploadTopicsCommand.getAiTopicsCsvFilePath());
            }
        }
    }

    /**
     * Generates a topic for public entries by asking the GPT-3.5-turbo-16k AI model to summarize the content of the entry's primary descriptor.
     * @param topicGeneratorConfig
     * @param inputCsvFilePath
     */
    private void generateTopics(TopicGeneratorConfig topicGeneratorConfig, String inputCsvFilePath) {
        final ApiClient apiClient = setupApiClient(topicGeneratorConfig.dockstoreServerUrl());
        final Ga4Ghv20Api ga4Ghv20Api = new Ga4Ghv20Api(apiClient);
        final OpenAiService openAiService = new OpenAiService(topicGeneratorConfig.openaiApiKey());
        final String outputFileName = OUTPUT_FILE_PREFIX + "_" + AI_MODEL + "_" + Instant.now().truncatedTo(ChronoUnit.SECONDS).toString().replace("-", "").replace(":", "") + ".csv";
        final Iterable<CSVRecord> entriesCsvRecords = readCsvFile(inputCsvFilePath, GenerateTopicsCommand.InputCsvHeaders.class);

        try (CSVPrinter csvPrinter = new CSVPrinter(new FileWriter(outputFileName, StandardCharsets.UTF_8), CSVFormat.DEFAULT.builder().setHeader(OutputCsvHeaders.class).build())) {
            for (CSVRecord entry: entriesCsvRecords) {
                final String trsId = entry.get(GenerateTopicsCommand.InputCsvHeaders.trsId);
                final String versionId = entry.get(GenerateTopicsCommand.InputCsvHeaders.version);

                // Get descriptor file content and entry type
                FileWrapper descriptorFile;
                String entryType;
                DescriptorTypeEnum descriptorType;
                try {
                    entryType = ga4Ghv20Api.toolsIdGet(trsId).getToolclass().getName().toLowerCase();
                    final ToolVersion version = ga4Ghv20Api.toolsIdVersionsVersionIdGet(trsId, versionId);
                    descriptorType = version.getDescriptorType().get(0);
                    descriptorFile = ga4Ghv20Api.toolsIdVersionsVersionIdTypeDescriptorGet(trsId, descriptorType.toString(), versionId);

                    if (descriptorType == DescriptorTypeEnum.NFL) {
                        // For nextflow workflows, find the main script. Otherwise, use the nextflow.config file (which is a nextflow workflow's primary descriptor in Dockstore terms)
                        Optional<FileWrapper> nextflowMainScript = getNextflowMainScript(descriptorFile.getContent(), ga4Ghv20Api, trsId, versionId, descriptorType);
                        if (nextflowMainScript.isPresent()) {
                            descriptorFile = nextflowMainScript.get();
                        }
                    }
                } catch (ApiException ex) {
                    LOG.error("Could not get entry with TRS ID {} and version {}, skipping", trsId, versionId, ex);
                    continue;
                }

                // Create ChatGPT request
                try {
                    getAiGeneratedTopicAndRecordToCsv(openAiService, csvPrinter, trsId, versionId, entryType, descriptorFile);
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
     * @param descriptorFile
     */
    private void getAiGeneratedTopicAndRecordToCsv(OpenAiService openAiService, CSVPrinter csvPrinter, String trsId, String versionId, String entryType, FileWrapper descriptorFile) {
        // A character limit is specified but ChatGPT doesn't follow it strictly
        final String systemPrompt = "Summarize the " + entryType + " in one sentence that starts with a verb. Use a maximum of 150 characters.";
        final ChatMessage systemMessage = new ChatMessage(ChatMessageRole.SYSTEM.value(), systemPrompt);
        // The sum of the number of tokens in the request and response cannot exceed the model's maximum context length.
        final int maxResponseTokens = 100; // One token is roughly 4 characters. Using 100 tokens because setting it too low might truncate the response
        // Chat completion API calls include additional tokens for message-based formatting. Calculate how long the descriptor content can be and truncate if needed
        final int maxUserMessageTokens = OpenAIHelper.getMaximumAmountOfTokensForUserMessageContent(REGISTRY, AI_MODEL, MAX_CONTEXT_LENGTH, systemMessage, maxResponseTokens);
        final EncodingResult encoded = ENCODING.encode(descriptorFile.getContent(), maxUserMessageTokens); // Encodes the content up to the maximum number of tokens specified
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
        String descriptorFileChecksum = descriptorFile.getChecksum().isEmpty() ? "" : descriptorFile.getChecksum().get(0).getChecksum();

        // Write response to new CSV file
        try {
            csvPrinter.printRecord(trsId, versionId, descriptorFile.getUrl(), descriptorFileChecksum, encoded.isTruncated(), promptTokens, completionTokens, finishReason, aiGeneratedTopic);
        } catch (IOException e) {
            LOG.error("Unable to write CSV record to file, skipping", e);
        }
    }

    private Optional<FileWrapper> getNextflowMainScript(String nextflowConfigFileContent, Ga4Ghv20Api ga4Ghv20Api, String trsId, String versionId, DescriptorTypeEnum descriptorType) {
        final String mainScriptPath = NextflowUtilities.grabConfig(nextflowConfigFileContent).getString("manifest.mainScript", "main.nf");
        try {
            return Optional.of(ga4Ghv20Api.toolsIdVersionsVersionIdTypeDescriptorRelativePathGet(trsId, descriptorType.toString(), versionId, mainScriptPath));
        } catch (ApiException exception) {
            LOG.error("Could not get Nextflow main script {}", mainScriptPath, exception);
            return Optional.empty();
        }
    }

    private void uploadTopics(TopicGeneratorConfig topicGeneratorConfig, String inputCsvFilePath) {
        final ApiClient apiClient = setupApiClient(topicGeneratorConfig.dockstoreServerUrl(), topicGeneratorConfig.dockstoreToken());
        final ExtendedGa4GhApi extendedGa4GhApi = new ExtendedGa4GhApi(apiClient);
        final Iterable<CSVRecord> entriesWithAITopics = readCsvFile(inputCsvFilePath, GenerateTopicsCommand.OutputCsvHeaders.class);

        for (CSVRecord entryWithAITopic: entriesWithAITopics) {
            // This command's input CSV headers are the generate-topic command's output headers
            final String trsId = entryWithAITopic.get(GenerateTopicsCommand.OutputCsvHeaders.trsId);
            final String aiTopic = entryWithAITopic.get(GenerateTopicsCommand.OutputCsvHeaders.aiTopic);
            boolean caughtByFilter = assessTopic(aiTopic);
            if (caughtByFilter) {
                LOG.info("Topic for {} was deemed offensive, please review above", trsId);
                continue;
            }
            final String version = entryWithAITopic.get(OutputCsvHeaders.version);
            try {
                extendedGa4GhApi.updateAITopic(new UpdateAITopicRequest().aiTopic(aiTopic), version, trsId);
                LOG.info("Uploaded AI topic for {}", trsId);
            } catch (ApiException exception) {
                LOG.error("Could not upload AI topic for {}", trsId);
            }
        }
    }

    private boolean assessTopic(String aiTopic) {
        for (StringFilter filter : this.stringFilters) {
            if (filter.assessTopic(aiTopic)) {
                LOG.info(filter.getClass() + " blocked a topic sentence, please review: " + aiTopic);
                return true;
            }
        }
        return false;
    }

    private Iterable<CSVRecord> readCsvFile(String inputCsvFilePath, Class<? extends Enum<?>> csvHeaders) {
        // Read CSV file
        Iterable<CSVRecord> csvRecords = null;
        try {
            final Reader entriesCsv = new FileReader(inputCsvFilePath);
            CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
                    .setHeader(csvHeaders)
                    .setSkipHeaderRecord(true)
                    .setTrim(true)
                    .build();
            csvRecords = csvFormat.parse(entriesCsv);
        } catch (IOException e) {
            exceptionMessage(e, "Unable to read input CSV file", IO_ERROR);
        }
        return csvRecords;
    }
}

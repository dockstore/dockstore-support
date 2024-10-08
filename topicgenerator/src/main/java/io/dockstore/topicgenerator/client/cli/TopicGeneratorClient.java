package io.dockstore.topicgenerator.client.cli;

import static io.dockstore.utils.ConfigFileUtils.getConfiguration;
import static io.dockstore.utils.DockstoreApiClientUtils.setupApiClient;
import static io.dockstore.utils.ExceptionHandler.CLIENT_ERROR;
import static io.dockstore.utils.ExceptionHandler.GENERIC_ERROR;
import static io.dockstore.utils.ExceptionHandler.IO_ERROR;
import static io.dockstore.utils.ExceptionHandler.errorMessage;
import static io.dockstore.utils.ExceptionHandler.errorMessage;
import static io.dockstore.utils.ExceptionHandler.exceptionMessage;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.MissingCommandException;
import com.beust.jcommander.ParameterException;
import com.google.common.collect.Lists;
import io.dockstore.common.NextflowUtilities;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.ApiException;
import io.dockstore.openapi.client.api.ExtendedGa4GhApi;
import io.dockstore.openapi.client.api.Ga4Ghv20Api;
import io.dockstore.openapi.client.model.EntryLiteAndVersionName;
import io.dockstore.openapi.client.model.FileWrapper;
import io.dockstore.openapi.client.model.ToolVersion;
import io.dockstore.openapi.client.model.ToolVersion.DescriptorTypeEnum;
import io.dockstore.openapi.client.model.UpdateAITopicRequest;
import io.dockstore.topicgenerator.client.cli.TopicGeneratorCommandLineArgs.GenerateTopicsCommand;
import io.dockstore.topicgenerator.client.cli.TopicGeneratorCommandLineArgs.GenerateTopicsCommand.InputCsvHeaders;
import io.dockstore.topicgenerator.client.cli.TopicGeneratorCommandLineArgs.GenerateTopicsCommand.OutputCsvHeaders;
import io.dockstore.topicgenerator.client.cli.TopicGeneratorCommandLineArgs.GetTopicCandidates;
import io.dockstore.topicgenerator.client.cli.TopicGeneratorCommandLineArgs.UploadTopicsCommand;
import io.dockstore.topicgenerator.helper.AIModelType;
import io.dockstore.topicgenerator.helper.AnthropicClaudeModel;
import io.dockstore.topicgenerator.helper.BaseAIModel;
import io.dockstore.topicgenerator.helper.CSVHelper;
import io.dockstore.topicgenerator.helper.ChuckNorrisFilter;
import io.dockstore.topicgenerator.helper.OpenAIModel;
import io.dockstore.topicgenerator.helper.StringFilter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TopicGeneratorClient {
    public static final String OUTPUT_FILE_PREFIX = "generated-topics";
    private static final Logger LOG = LoggerFactory.getLogger(TopicGeneratorClient.class);
    private final List<StringFilter> stringFilters = Lists.newArrayList(new ChuckNorrisFilter("en"), new ChuckNorrisFilter("fr-CA-u-sd-caqc"));

    TopicGeneratorClient() {
    }

    public static void main(String[] args) {
        final TopicGeneratorCommandLineArgs commandLineArgs = new TopicGeneratorCommandLineArgs();
        final JCommander jCommander = new JCommander(commandLineArgs);
        final GetTopicCandidates getTopicCandidates = new GetTopicCandidates();
        final GenerateTopicsCommand generateTopicsCommand = new GenerateTopicsCommand();
        final UploadTopicsCommand uploadTopicsCommand = new UploadTopicsCommand();
        jCommander.addCommand(getTopicCandidates);
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

            switch (jCommander.getParsedCommand()) {
            case "get-topic-candidates" -> topicGeneratorClient.getAITopicCandidates(topicGeneratorConfig, getTopicCandidates.getEntriesCsvOutputFilePath());
            case "generate-topics" -> topicGeneratorClient.generateTopics(topicGeneratorConfig, generateTopicsCommand.getEntriesCsvFilePath());
            case "upload-topics" -> topicGeneratorClient.uploadTopics(topicGeneratorConfig, uploadTopicsCommand.getAiTopicsCsvFilePath());
            default -> errorMessage("Unknown command", GENERIC_ERROR);
            }
        }
    }

    public void getAITopicCandidates(TopicGeneratorConfig topicGeneratorConfig, String outputCsvFilePath) {
        final ApiClient apiClient = setupApiClient(topicGeneratorConfig.dockstoreServerUrl(), topicGeneratorConfig.dockstoreToken());
        final ExtendedGa4GhApi extendedGa4GhApi = new ExtendedGa4GhApi(apiClient);
        LOG.info("Getting AI topic candidates from {}", topicGeneratorConfig.dockstoreServerUrl());

        List<EntryLiteAndVersionName> aiTopicCandidates = extendedGa4GhApi.getAITopicCandidates();
        LOG.info("There are {} AI topic candidates", aiTopicCandidates.size());

        if (aiTopicCandidates.isEmpty()) {
            LOG.info("No AI topic candidates found");
            return;
        }

        try (CSVPrinter csvPrinter = new CSVPrinter(new FileWriter(outputCsvFilePath, StandardCharsets.UTF_8), CSVFormat.DEFAULT.builder().setHeader(GenerateTopicsCommand.InputCsvHeaders.class).build())) {
            aiTopicCandidates.forEach(aiTopicCandidate -> {
                try {
                    csvPrinter.printRecord(aiTopicCandidate.getEntryLite().getTrsId(), aiTopicCandidate.getVersionName());
                } catch (IOException e) {
                    LOG.error("Could not write record for TRS ID {}, version {}", aiTopicCandidate.getEntryLite().getTrsId(), aiTopicCandidate.getVersionName());
                }
            });
            LOG.info("View AI topic candidates for {} in file {}", topicGeneratorConfig.dockstoreServerUrl(), outputCsvFilePath);
        } catch (IOException e) {
            exceptionMessage(e, "Unable to create new CSV output file", IO_ERROR);
        }
    }

    /**
     * Generates a topic for public entries by asking the AI model to summarize the content of the entry's primary descriptor.
     * @param topicGeneratorConfig
     * @param inputCsvFilePath
     */
    private void generateTopics(TopicGeneratorConfig topicGeneratorConfig, String inputCsvFilePath, AIModelType aiModelType) {
        final ApiClient apiClient = setupApiClient(topicGeneratorConfig.dockstoreServerUrl());
        final Ga4Ghv20Api ga4Ghv20Api = new Ga4Ghv20Api(apiClient);

        BaseAIModel aiModel = null;
        if (aiModelType == AIModelType.CLAUDE_3_HAIKU || aiModelType == AIModelType.CLAUDE_3_5_SONNET) {
            aiModel = new AnthropicClaudeModel(aiModelType);
        } else if (aiModelType == AIModelType.GPT_4O_MINI) {
            if (StringUtils.isEmpty(topicGeneratorConfig.openaiApiKey())) {
                errorMessage("OpenAI API key is required in the config file to use an OpenAI model", CLIENT_ERROR);
            }
            aiModel = new OpenAIModel(topicGeneratorConfig.openaiApiKey(), aiModelType);
        } else {
            errorMessage("Invalid AI model type", CLIENT_ERROR);
        }

        LOG.info("Generating topics using {}", aiModelType.getModelId());

        final String outputFileName = OUTPUT_FILE_PREFIX + "_" + aiModelType + "_" + Instant.now().truncatedTo(ChronoUnit.SECONDS).toString().replace("-", "").replace(":", "") + ".csv";
        final Iterable<CSVRecord> entriesCsvRecords = CSVHelper.readFile(inputCsvFilePath, InputCsvHeaders.class);

        try (CSVPrinter csvPrinter = new CSVPrinter(new FileWriter(outputFileName, StandardCharsets.UTF_8), CSVFormat.DEFAULT.builder().setHeader(OutputCsvHeaders.class).build())) {
            for (CSVRecord entry: entriesCsvRecords) {
                final String trsId = entry.get(InputCsvHeaders.trsId);
                final String versionId = entry.get(InputCsvHeaders.version);

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

                // Create AI request
                try {
                    String prompt = "Summarize the " + entryType + " in one sentence that starts with a present tense verb in the <summary> tags. Use a maximum of 150 characters.\n<content>" + descriptorFile.getContent() + "</content>";
                    FileWrapper finalDescriptorFile = descriptorFile;
                    aiModel.submitPrompt(prompt).ifPresentOrElse(
                            aiResponseInfo -> {
                                CSVHelper.writeRecord(csvPrinter, trsId, versionId, finalDescriptorFile, aiResponseInfo);
                                LOG.info("Generated topic for entry with TRS ID {} and version {}", trsId, versionId);
                            },
                            () -> LOG.error("Unable to generate topic for entry with TRS ID {} and version {}, skipping", trsId, versionId)
                    );
                } catch (Exception ex) {
                    LOG.error("Unable to generate topic for entry with TRS ID {} and version {}, skipping", trsId, versionId, ex);
                }
            }
            LOG.info("View generated topics in file {}", outputFileName);
        } catch (IOException e) {
            exceptionMessage(e, "Unable to create new CSV output file", IO_ERROR);
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
        final Iterable<CSVRecord> entriesWithAITopics = CSVHelper.readFile(inputCsvFilePath, OutputCsvHeaders.class);

        for (CSVRecord entryWithAITopic: entriesWithAITopics) {
            // This command's input CSV headers are the generate-topic command's output headers
            final String trsId = entryWithAITopic.get(OutputCsvHeaders.trsId);
            final String aiTopic = entryWithAITopic.get(OutputCsvHeaders.aiTopic);
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
            if (filter.isSuspiciousTopic(aiTopic)) {
                LOG.info(filter.getClass() + " blocked a topic sentence, please review: " + aiTopic);
                return true;
            }
        }
        return false;
    }

    public static String removeSummaryTagsFromTopic(String aiTopic) {
        String cleanedTopic = StringUtils.removeStart(aiTopic, "<summary>");
        return StringUtils.removeEnd(cleanedTopic, "</summary>");
    }
}

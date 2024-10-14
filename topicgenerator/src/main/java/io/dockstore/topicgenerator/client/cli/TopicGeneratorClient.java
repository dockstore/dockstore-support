package io.dockstore.topicgenerator.client.cli;

import static io.dockstore.utils.ConfigFileUtils.getConfiguration;
import static io.dockstore.utils.DockstoreApiClientUtils.setupApiClient;
import static io.dockstore.utils.ExceptionHandler.API_ERROR;
import static io.dockstore.utils.ExceptionHandler.CLIENT_ERROR;
import static io.dockstore.utils.ExceptionHandler.GENERIC_ERROR;
import static io.dockstore.utils.ExceptionHandler.IO_ERROR;
import static io.dockstore.utils.ExceptionHandler.errorMessage;
import static io.dockstore.utils.ExceptionHandler.exceptionMessage;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.MissingCommandException;
import com.beust.jcommander.ParameterException;
import com.google.common.collect.Lists;
import io.dockstore.common.NextflowUtilities;
import io.dockstore.common.NextflowUtilities.NextflowParsingException;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.ApiException;
import io.dockstore.openapi.client.api.ExtendedGa4GhApi;
import io.dockstore.openapi.client.api.Ga4Ghv20Api;
import io.dockstore.openapi.client.model.FileWrapper;
import io.dockstore.openapi.client.model.Tool;
import io.dockstore.openapi.client.model.ToolVersion;
import io.dockstore.openapi.client.model.ToolVersion.DescriptorTypeEnum;
import io.dockstore.openapi.client.model.UpdateAITopicRequest;
import io.dockstore.topicgenerator.client.cli.TopicGeneratorCommandLineArgs.GenerateTopicsCommand;
import io.dockstore.topicgenerator.client.cli.TopicGeneratorCommandLineArgs.GenerateTopicsCommand.ErrorsCsvHeaders;
import io.dockstore.topicgenerator.client.cli.TopicGeneratorCommandLineArgs.GenerateTopicsCommand.InputCsvHeaders;
import io.dockstore.topicgenerator.client.cli.TopicGeneratorCommandLineArgs.GenerateTopicsCommand.OutputCsvHeaders;
import io.dockstore.topicgenerator.client.cli.TopicGeneratorCommandLineArgs.UploadTopicsCommand;
import io.dockstore.topicgenerator.helper.AIModelType;
import io.dockstore.topicgenerator.helper.AnthropicClaudeModel;
import io.dockstore.topicgenerator.helper.BaseAIModel;
import io.dockstore.topicgenerator.helper.BaseAIModel.AIResponseInfo;
import io.dockstore.topicgenerator.helper.CSVHelper;
import io.dockstore.topicgenerator.helper.ChuckNorrisFilter;
import io.dockstore.topicgenerator.helper.OpenAIModel;
import io.dockstore.topicgenerator.helper.StringFilter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.ObjectUtils;
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
        final Instant startTime = Instant.now();
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

            switch (jCommander.getParsedCommand()) {
            case "generate-topics" -> topicGeneratorClient.generateTopics(topicGeneratorConfig, generateTopicsCommand);
            case "upload-topics" -> topicGeneratorClient.uploadTopics(topicGeneratorConfig, uploadTopicsCommand.getAiTopicsCsvFilePath());
            default -> errorMessage("Unknown command", GENERIC_ERROR);
            }
        }

        if (jCommander.getParsedCommand() != null) {
            final Instant endTime = Instant.now();
            LOG.info("{} took {}", jCommander.getParsedCommand(), Duration.between(startTime, endTime));
        }
    }

    /**
     * Generates a topic for public entries by asking the AI model to summarize the content of the entry's primary descriptor.
     * @param topicGeneratorConfig
     */
    private void generateTopics(TopicGeneratorConfig topicGeneratorConfig, GenerateTopicsCommand generateTopicsCommand) {
        final String dockstoreServerUrl = topicGeneratorConfig.dockstoreServerUrl();
        final ApiClient apiClient = setupApiClient(dockstoreServerUrl, topicGeneratorConfig.dockstoreToken());
        final Ga4Ghv20Api ga4Ghv20Api = new Ga4Ghv20Api(apiClient);
        final ExtendedGa4GhApi extendedGa4GhApi = new ExtendedGa4GhApi(apiClient);
        final AIModelType aiModelType = generateTopicsCommand.getAiModel();
        final String inputFileName = generateTopicsCommand.getEntriesCsvFilePath();

        List<TrsIdAndVersionId> aiTopicCandidates;
        if (inputFileName != null) {
            aiTopicCandidates = getAiTopicCandidatesFromFile(generateTopicsCommand.getEntriesCsvFilePath());
        } else {
            aiTopicCandidates = getAiTopicCandidatesFromDockstore(extendedGa4GhApi, generateTopicsCommand.getMax());
        }

        if (aiTopicCandidates.isEmpty()) {
            LOG.info("No AI topic candidates to process");
            return;
        }

        if (generateTopicsCommand.isDryRun()) {
            if (inputFileName == null) {
                writeAITopicCandidates(aiTopicCandidates);
            } else {
                LOG.info("View the AI topic candidates in input file {}", inputFileName);
            }
            return;
        }

        Optional<BaseAIModel> aiModel = getAiModel(aiModelType, topicGeneratorConfig);
        if (aiModel.isEmpty()) {
            errorMessage("Invalid AI model type", CLIENT_ERROR);
        }
        LOG.info("Generating topics for AI topic candidates using AI model {}", aiModelType.getModelId());
        final String outputFileNameSuffix = "_" + aiModelType + "_" + Instant.now().truncatedTo(ChronoUnit.SECONDS).toString().replace("-", "").replace(":", "") + ".csv";
        final String generatedTopicsFileName = OUTPUT_FILE_PREFIX + outputFileNameSuffix;
        final String errorsFileName = "errors" + outputFileNameSuffix;
        int numberOfTopicsGenerated = 0;
        int numberOfFailures = 0;
        try (CSVPrinter csvPrinter = new CSVPrinter(new FileWriter(generatedTopicsFileName, StandardCharsets.UTF_8), CSVFormat.DEFAULT.builder().setHeader(OutputCsvHeaders.class).build());
                CSVPrinter errorsCsvPrinter = new CSVPrinter(new FileWriter(errorsFileName, StandardCharsets.UTF_8), CSVFormat.DEFAULT.builder().setHeader(ErrorsCsvHeaders.class).build())) {
            for (TrsIdAndVersionId aiTopicCandidate: aiTopicCandidates) {
                final String trsId = aiTopicCandidate.trsId();
                final String versionId = aiTopicCandidate.versionId();
                if (StringUtils.isEmpty(versionId)) {
                    LOG.error("Unable to generate topic for entry with TRS ID '{}' and version '{}' because version name is empty, skipping", trsId, versionId);
                    errorsCsvPrinter.printRecord(trsId, versionId, "Version name is empty");
                    numberOfFailures += 1;
                    continue;
                }

                // Get required information to create a prompt
                final String entryType;
                final FileWrapper descriptorFile;
                try {
                    final Tool tool = ga4Ghv20Api.toolsIdGet(trsId);
                    entryType = tool.getToolclass().getName().toLowerCase();
                    final List<ToolVersion> filteredVersion = tool.getVersions().stream()
                            .filter(v -> v.getName().equals(aiTopicCandidate.versionId())).toList();
                    if (filteredVersion.isEmpty()) {
                        LOG.error(
                                "Unable to generate topic for entry with TRS ID '{}' and version '{}' because could not retrieve version, skipping",
                                trsId, versionId);
                        errorsCsvPrinter.printRecord(trsId, versionId, "Could not retrieve version");
                        numberOfFailures += 1;
                        continue;
                    }

                    final ToolVersion version = filteredVersion.get(0);
                    descriptorFile = getDescriptorFile(ga4Ghv20Api, trsId, versionId, version.getDescriptorType());
                } catch (ApiException ex) {
                    LOG.error("Failed to get information for AI topic candidate with TRS ID {} and version {} from Dockstore, skipping", trsId, versionId, ex);
                    errorsCsvPrinter.printRecord(trsId, versionId, ex.getMessage().replace("\n", " "));
                    numberOfFailures += 1;
                    continue;
                }

                // Generate topic using AI model
                try {
                    String prompt = "Summarize the " + entryType
                            + " in one sentence that starts with a present tense verb in the <summary> tags. Use a maximum of 150 characters.\n<content>"
                            + descriptorFile.getContent() + "</content>";
                    AIResponseInfo aiResponseInfo = aiModel.get().submitPrompt(prompt);
                    CSVHelper.writeRecord(csvPrinter, trsId, versionId, descriptorFile, aiResponseInfo);
                    LOG.info("Generated topic for entry with TRS ID {} and version {}", trsId, versionId);
                    numberOfTopicsGenerated += 1;
                } catch (Exception ex) {
                    LOG.error("Unable to generate topic for entry with TRS ID {} and version {}, skipping", trsId, versionId, ex);
                    errorsCsvPrinter.printRecord(trsId, versionId, ex.getMessage());
                    numberOfFailures += 1;
                }
            }

            LOG.info("Generated {} AI topics. View generated AI topics in file {}", numberOfTopicsGenerated, generatedTopicsFileName);
            LOG.info("Failed to generate topics for {} entries. View entries that failed AI topic generation in file {}", numberOfFailures, errorsFileName);
        } catch (IOException e) {
            exceptionMessage(e, "Unable to create new CSV output file", IO_ERROR);
        }
    }

    /**
     * Generates a topic for public entries by asking the AI model to summarize the content of the entry's primary descriptor.
     * @param topicGeneratorConfig
     */
    private void generateTopics(TopicGeneratorConfig topicGeneratorConfig, GenerateTopicsCommand generateTopicsCommand) {
        final String dockstoreServerUrl = topicGeneratorConfig.dockstoreServerUrl();
        final ApiClient apiClient = setupApiClient(dockstoreServerUrl, topicGeneratorConfig.dockstoreToken());
        final Ga4Ghv20Api ga4Ghv20Api = new Ga4Ghv20Api(apiClient);
        final ExtendedGa4GhApi extendedGa4GhApi = new ExtendedGa4GhApi(apiClient);
        final AIModelType aiModelType = generateTopicsCommand.getAiModel();
        final String inputFileName = generateTopicsCommand.getEntriesCsvFilePath();

        List<TrsIdAndVersionId> aiTopicCandidates;
        if (inputFileName != null) {
            aiTopicCandidates = getAiTopicCandidatesFromFile(generateTopicsCommand.getEntriesCsvFilePath());
        } else {
            aiTopicCandidates = getAiTopicCandidatesFromDockstore(extendedGa4GhApi, generateTopicsCommand.getMax());
        }

        if (aiTopicCandidates.isEmpty()) {
            LOG.info("No AI topic candidates to process");
            return;
        }

        if (generateTopicsCommand.isDryRun()) {
            if (inputFileName == null) {
                writeAITopicCandidates(aiTopicCandidates);
            } else {
                LOG.info("View the AI topic candidates in input file {}", inputFileName);
            }
            return;
        }

        Optional<BaseAIModel> aiModel = getAiModel(aiModelType, topicGeneratorConfig);
        if (aiModel.isEmpty()) {
            errorMessage("Invalid AI model type", CLIENT_ERROR);
        }
        LOG.info("Generating topics for AI topic candidates using AI model {}", aiModelType.getModelId());
        final String outputFileNameSuffix = "_" + aiModelType + "_" + Instant.now().truncatedTo(ChronoUnit.SECONDS).toString().replace("-", "").replace(":", "") + ".csv";
        final String generatedTopicsFileName = OUTPUT_FILE_PREFIX + outputFileNameSuffix;
        final String errorsFileName = "errors" + outputFileNameSuffix;
        int numberOfTopicsGenerated = 0;
        int numberOfFailures = 0;
        try (CSVPrinter csvPrinter = new CSVPrinter(new FileWriter(generatedTopicsFileName, StandardCharsets.UTF_8), CSVFormat.DEFAULT.builder().setHeader(OutputCsvHeaders.class).build());
                CSVPrinter errorsCsvPrinter = new CSVPrinter(new FileWriter(errorsFileName, StandardCharsets.UTF_8), CSVFormat.DEFAULT.builder().setHeader(ErrorsCsvHeaders.class).build())) {
            for (TrsIdAndVersionId aiTopicCandidate: aiTopicCandidates) {
                final String trsId = aiTopicCandidate.trsId();
                final String versionId = aiTopicCandidate.versionId();
                if (StringUtils.isEmpty(versionId)) {
                    LOG.error("Unable to generate topic for entry with TRS ID '{}' and version '{}' because version name is empty, skipping", trsId, versionId);
                    errorsCsvPrinter.printRecord(trsId, versionId, "Version name is empty");
                    numberOfFailures += 1;
                    continue;
                }

                // Get required information to create a prompt
                final String entryType;
                final FileWrapper descriptorFile;
                try {
                    final Tool tool = ga4Ghv20Api.toolsIdGet(trsId);
                    entryType = tool.getToolclass().getName().toLowerCase();
                    final List<ToolVersion> filteredVersion = tool.getVersions().stream()
                            .filter(v -> v.getName().equals(aiTopicCandidate.versionId())).toList();
                    if (filteredVersion.isEmpty()) {
                        LOG.error(
                                "Unable to generate topic for entry with TRS ID '{}' and version '{}' because could not retrieve version, skipping",
                                trsId, versionId);
                        errorsCsvPrinter.printRecord(trsId, versionId, "Could not retrieve version");
                        numberOfFailures += 1;
                        continue;
                    }

                    final ToolVersion version = filteredVersion.get(0);
                    descriptorFile = getDescriptorFile(ga4Ghv20Api, trsId, versionId, version.getDescriptorType());
                } catch (ApiException ex) {
                    LOG.error("Failed to get information for AI topic candidate with TRS ID {} and version {} from Dockstore, skipping", trsId, versionId, ex);
                    errorsCsvPrinter.printRecord(trsId, versionId, ex.getMessage().replace("\n", " "));
                    numberOfFailures += 1;
                    continue;
                }

                // Generate topic using AI model
                try {
                    String prompt = "Summarize the " + entryType
                            + " in one sentence that starts with a present tense verb in the <summary> tags. Use a maximum of 150 characters.\n<content>"
                            + descriptorFile.getContent() + "</content>";
                    AIResponseInfo aiResponseInfo = aiModel.get().submitPrompt(prompt);
                    CSVHelper.writeRecord(csvPrinter, trsId, versionId, descriptorFile, aiResponseInfo);
                    LOG.info("Generated topic for entry with TRS ID {} and version {}", trsId, versionId);
                    numberOfTopicsGenerated += 1;
                } catch (Exception ex) {
                    LOG.error("Unable to generate topic for entry with TRS ID {} and version {}, skipping", trsId, versionId, ex);
                    errorsCsvPrinter.printRecord(trsId, versionId, ex.getMessage());
                    numberOfFailures += 1;
                }
            }

            LOG.info("Generated {} AI topics. View generated AI topics in file {}", numberOfTopicsGenerated, generatedTopicsFileName);
            LOG.info("Failed to generate topics for {} entries. View entries that failed AI topic generation in file {}", numberOfFailures, errorsFileName);
        } catch (IOException e) {
            exceptionMessage(e, "Unable to create new CSV output file", IO_ERROR);
        }
    }

    private List<TrsIdAndVersionId> getAiTopicCandidatesFromFile(String inputFileName) {
        List<TrsIdAndVersionId> aiTopicCandidates = new ArrayList<>();
        final Iterable<CSVRecord> entriesCsvRecords = CSVHelper.readFile(inputFileName, InputCsvHeaders.class);
        for (CSVRecord entry: entriesCsvRecords) {
            final String trsId = entry.get(InputCsvHeaders.trsId);
            final String versionId = entry.get(InputCsvHeaders.version);
            aiTopicCandidates.add(new TrsIdAndVersionId(trsId, versionId));
        }
        LOG.info("Retrieved {} AI topic candidates from input file {}", aiTopicCandidates.size(), inputFileName);
        return aiTopicCandidates;
    }

    private List<TrsIdAndVersionId> getAiTopicCandidatesFromDockstore(ExtendedGa4GhApi extendedGa4GhApi, Integer maxCandidates) {
        final String dockstoreServerUrl = extendedGa4GhApi.getApiClient().getBasePath();
        List<TrsIdAndVersionId> aiTopicCandidates = new ArrayList<>();
        final int maxPaginationLimit = 1000;
        if (maxCandidates == null) {
            LOG.info("No maximum specified. Retrieving all AI topic candidates from Dockstore {}", dockstoreServerUrl);
        } else if (maxCandidates > 0) {
            LOG.info("Retrieving a maximum of {} AI topic candidates from Dockstore {}", maxCandidates, dockstoreServerUrl);
        } else {
            errorMessage("--max must be greater than 0", CLIENT_ERROR);
        }

        final int paginationLimit = Math.min(ObjectUtils.firstNonNull(maxCandidates, maxPaginationLimit), maxPaginationLimit);
        int pageNumber = 1;
        Integer totalAiTopicCandidatesCount = null;
        while (maxCandidates == null || aiTopicCandidates.size() < maxCandidates) {
            final int offset = (pageNumber - 1) * paginationLimit;
            try {
                final List<TrsIdAndVersionId> aiTopicCandidatesFromDockstore = extendedGa4GhApi.getAITopicCandidates(offset, paginationLimit).stream()
                        .map(entryLiteAndVersionName -> new TrsIdAndVersionId(entryLiteAndVersionName.getEntryLite().getTrsId(), entryLiteAndVersionName.getVersionName()))
                        .toList();
                aiTopicCandidates.addAll(aiTopicCandidatesFromDockstore);
            } catch (ApiException exception) {
                exceptionMessage(exception, "Could not get AI topic candidates from Dockstore", API_ERROR);
            }

            if (totalAiTopicCandidatesCount == null) {
                try {
                    totalAiTopicCandidatesCount = Integer.parseInt(
                            extendedGa4GhApi.getApiClient().getResponseHeaders().get("X-total-count").get(0));
                } catch (Exception exception) {
                    exceptionMessage(exception, "Could not get X-total-count header value for AI topic candidates", API_ERROR);
                }
            }

            if (maxCandidates == null || maxCandidates > totalAiTopicCandidatesCount) {
                maxCandidates = totalAiTopicCandidatesCount;
            }
            pageNumber += 1;
        }

        LOG.info("Retrieved {} out of {} AI topic candidates from {}", aiTopicCandidates.size(), totalAiTopicCandidatesCount, dockstoreServerUrl);
        return aiTopicCandidates;
    }

    private Optional<BaseAIModel> getAiModel(AIModelType aiModelType, TopicGeneratorConfig topicGeneratorConfig) {
        if (aiModelType == AIModelType.CLAUDE_3_HAIKU || aiModelType == AIModelType.CLAUDE_3_5_SONNET) {
            return Optional.of(new AnthropicClaudeModel(aiModelType));
        } else if (aiModelType == AIModelType.GPT_4O_MINI) {
            if (StringUtils.isEmpty(topicGeneratorConfig.openaiApiKey())) {
                errorMessage("OpenAI API key is required in the config file to use an OpenAI model", CLIENT_ERROR);
            }
            return Optional.of(new OpenAIModel(topicGeneratorConfig.openaiApiKey(), aiModelType));
        } else {
            return Optional.empty();
        }
    }

    private void writeAITopicCandidates(List<TrsIdAndVersionId> aiTopicCandidates) {
        final String outputFileName = "ai-topic-candidates_" + Instant.now().truncatedTo(ChronoUnit.SECONDS).toString().replace("-", "").replace(":", "") + ".csv";
        try (CSVPrinter csvPrinter = new CSVPrinter(new FileWriter(outputFileName, StandardCharsets.UTF_8), CSVFormat.DEFAULT.builder().setHeader(InputCsvHeaders.class).build())) {
            for (TrsIdAndVersionId aiTopicCandidate: aiTopicCandidates) {
                CSVHelper.writeRecord(csvPrinter, aiTopicCandidate.trsId(), aiTopicCandidate.versionId());
            }
        } catch (IOException e) {
            exceptionMessage(e, "Unable to create new CSV output file", IO_ERROR);
        }
        LOG.info("View the AI topic candidates in file {}", outputFileName);
    }

    private FileWrapper getDescriptorFile(Ga4Ghv20Api ga4Ghv20Api, String trsId, String versionId, List<DescriptorTypeEnum> descriptorTypes) throws ApiException {
        // Get descriptor file content
        FileWrapper descriptorFile = null;
        for (int i = 0; i < descriptorTypes.size(); ++i) {
            DescriptorTypeEnum descriptorType = descriptorTypes.get(i);
            try {
                descriptorFile = ga4Ghv20Api.toolsIdVersionsVersionIdTypeDescriptorGet(trsId, descriptorType.toString(), versionId);
            } catch (ApiException ex) {
                // Rethrow exception if this is the last descriptor type and no descriptor file was found. Otherwise, try the next descriptor type
                if (i == descriptorTypes.size() - 1) {
                    throw ex;
                }
                continue;
            }

            if (descriptorType == DescriptorTypeEnum.NFL) {
                // For nextflow workflows, find the main script. Otherwise, use the nextflow.config file (which is a nextflow workflow's primary descriptor in Dockstore terms)
                Optional<FileWrapper> nextflowMainScript = getNextflowMainScript(descriptorFile.getContent(), ga4Ghv20Api, trsId, versionId, descriptorType);
                if (nextflowMainScript.isPresent()) {
                    descriptorFile = nextflowMainScript.get();
                }
            }
        }

        return descriptorFile;
    }

    private Optional<FileWrapper> getNextflowMainScript(String nextflowConfigFileContent, Ga4Ghv20Api ga4Ghv20Api, String trsId, String versionId, DescriptorTypeEnum descriptorType) {
        final String mainScriptPath;
        try {
            mainScriptPath = NextflowUtilities.grabConfig(nextflowConfigFileContent).getString("manifest.mainScript", "main.nf");
        } catch (NextflowParsingException e) {
            LOG.error("Could not get grab config", e);
            return Optional.empty();
        }
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
        int numberOfTopicsUploaded = 0;
        int numberOfTopicsFailedToUpload = 0;
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
                numberOfTopicsUploaded += 1;
                LOG.info("Uploaded AI topic for {}", trsId);
            } catch (ApiException exception) {
                numberOfTopicsFailedToUpload += 1;
                LOG.error("Could not upload AI topic for {}", trsId, exception);
            }
        }
        LOG.info("Uploaded {} AI topics. Failed to upload {} AI topics", numberOfTopicsUploaded, numberOfTopicsFailedToUpload);
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

    public record TrsIdAndVersionId(String trsId, String versionId) {
    }
}

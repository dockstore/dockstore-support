package io.dockstore.categorizer.client.cli;

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
import io.dockstore.categorizer.client.cli.CategorizerCommandLineArgs.CategorizeEntriesCommand;
import io.dockstore.categorizer.client.cli.CategorizerCommandLineArgs.CategorizeEntriesCommand.ErrorsCsvHeaders;
import io.dockstore.categorizer.client.cli.CategorizerCommandLineArgs.CategorizeEntriesCommand.InputCsvHeaders;
import io.dockstore.categorizer.client.cli.CategorizerCommandLineArgs.CategorizeEntriesCommand.OutputCsvHeaders;
import io.dockstore.categorizer.client.cli.CategorizerCommandLineArgs.UploadCategoriesCommand;
import io.dockstore.common.NextflowUtilities;
import io.dockstore.common.NextflowUtilities.NextflowParsingException;
import io.dockstore.common.S3ClientHelper;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.ApiException;
import io.dockstore.openapi.client.api.ExtendedGa4GhApi;
import io.dockstore.openapi.client.api.Ga4Ghv20Api;
import io.dockstore.openapi.client.model.FileWrapper;
import io.dockstore.openapi.client.model.Tool;
import io.dockstore.openapi.client.model.ToolVersion;
import io.dockstore.openapi.client.model.ToolVersion.DescriptorTypeEnum;
import io.dockstore.utils.ai.AIModelType;
import io.dockstore.utils.ai.AnthropicClaudeModel;
import io.dockstore.utils.ai.BaseAIModel;
import io.dockstore.utils.ai.BaseAIModel.AIResponseInfo;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CategorizerClient {
    private static final Logger LOG = LoggerFactory.getLogger(CategorizerClient.class);

    CategorizerClient() {
    }

    public static void main(String[] args) {
        final Instant startTime = Instant.now();
        final CategorizerCommandLineArgs commandLineArgs = new CategorizerCommandLineArgs();
        final JCommander jCommander = new JCommander(commandLineArgs);
        final CategorizeEntriesCommand categorizeEntriesCommand = new CategorizeEntriesCommand();
        final UploadCategoriesCommand uploadCategoriesCommand = new UploadCategoriesCommand();
        jCommander.addCommand(categorizeEntriesCommand);
        jCommander.addCommand(uploadCategoriesCommand);

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
            final CategorizerConfig categorizerConfig = new CategorizerConfig(config);
            final CategorizerClient categorizerClient = new CategorizerClient();

            switch (jCommander.getParsedCommand()) {
            case "categorize-entries" -> categorizerClient.categorizeEntries(categorizerConfig, categorizeEntriesCommand);
            case "upload-categories" -> categorizerClient.uploadCategories(categorizerConfig, uploadCategoriesCommand);
            default -> errorMessage("Unknown command", GENERIC_ERROR);
            }
        }

        if (jCommander.getParsedCommand() != null) {
            final Instant endTime = Instant.now();
            LOG.info("{} took {}", jCommander.getParsedCommand(), Duration.between(startTime, endTime));
        }
    }

    /**
     * Categorizes public entries by asking the AI model to suggest ontology categories based on the entry's primary descriptor.
     * @param categorizerConfig
     * @param categorizeEntriesCommand
     */
    private void categorizeEntries(CategorizerConfig categorizerConfig, CategorizeEntriesCommand categorizeEntriesCommand) {
        final String dockstoreServerUrl = categorizerConfig.dockstoreServerUrl();
        final ApiClient apiClient = setupApiClient(dockstoreServerUrl, categorizerConfig.dockstoreToken());
        final Ga4Ghv20Api ga4Ghv20Api = new Ga4Ghv20Api(apiClient);
        final ExtendedGa4GhApi extendedGa4GhApi = new ExtendedGa4GhApi(apiClient);
        final AIModelType aiModelType = categorizeEntriesCommand.getAiModel();
        final String inputFileName = categorizeEntriesCommand.getEntriesCsvFilePath();

        List<TrsIdAndVersionId> categorizationCandidates;
        if (inputFileName != null) {
            categorizationCandidates = getCategorizationCandidatesFromFile(inputFileName);
        } else {
            categorizationCandidates = getCategorizationCandidatesFromDockstore(extendedGa4GhApi, categorizeEntriesCommand.getMax());
        }

        if (categorizationCandidates.isEmpty()) {
            LOG.info("No categorization candidates to process");
            return;
        }

        if (categorizeEntriesCommand.isDryRun()) {
            if (inputFileName == null) {
                writeCategorizationCandidates(categorizationCandidates);
            } else {
                LOG.info("View the categorization candidates in input file {}", inputFileName);
            }
            return;
        }

        Optional<BaseAIModel> aiModel = getAiModel(aiModelType);
        if (aiModel.isEmpty()) {
            errorMessage("Invalid AI model type", CLIENT_ERROR);
        }
        LOG.info("Categorizing entries using AI model {}", aiModelType.getModelId());
        final String outputFileNameSuffix = "_" + aiModelType + "_" + Instant.now().truncatedTo(ChronoUnit.SECONDS).toString().replace("-", "").replace(":", "") + ".csv";
        final String categoriesFileName = "generated-categories" + outputFileNameSuffix;
        final String errorsFileName = "errors" + outputFileNameSuffix;
        int numberOfCategoriesGenerated = 0;
        int numberOfFailures = 0;
        try (CSVPrinter categoriesCsvPrinter = createCsvPrinter(categoriesFileName, OutputCsvHeaders.class);
                CSVPrinter errorsCsvPrinter = createCsvPrinter(errorsFileName, ErrorsCsvHeaders.class)) {
            for (TrsIdAndVersionId candidate : categorizationCandidates) {
                final String trsId = candidate.trsId();
                final String versionId = candidate.versionId();
                if (StringUtils.isEmpty(versionId)) {
                    LOG.error("Unable to categorize entry with TRS ID '{}' and version '{}' because version name is empty, skipping", trsId, versionId);
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
                            .filter(v -> v.getName().equals(candidate.versionId())).toList();
                    if (filteredVersion.isEmpty()) {
                        LOG.error("Unable to categorize entry with TRS ID '{}' and version '{}' because could not retrieve version, skipping", trsId, versionId);
                        errorsCsvPrinter.printRecord(trsId, versionId, "Could not retrieve version");
                        numberOfFailures += 1;
                        continue;
                    }

                    final ToolVersion version = filteredVersion.get(0);
                    descriptorFile = getDescriptorFile(ga4Ghv20Api, trsId, versionId, version.getDescriptorType());
                } catch (ApiException ex) {
                    LOG.error("Failed to get information for categorization candidate with TRS ID {} and version {} from Dockstore, skipping", trsId, versionId, ex);
                    errorsCsvPrinter.printRecord(trsId, versionId, ex.getMessage().replace("\n", " "));
                    numberOfFailures += 1;
                    continue;
                }

                // Generate categories using AI model
                try {
                    /*
                    String prompt = "Based on the content of the following " + entryType
                            + ", suggest relevant EDAM ontology categories that best describe the operations, topics, and data types involved."
                            + " Return the categories as a JSON array of objects with 'uri' and 'label' fields in <categories> tags.\n<content>"
                            + descriptorFile.getContent() + "</content>";
                    */
                    String prompt = "Based on the following information about a " + entryType + ", determine the output format that the " + entryType + " supports.\n";
                    prompt += "<name>Optimus</name>\n";
                    prompt += "<description>\nIt is an alignment and transcriptome quantification pipeline that corrects cell barcodes (CBs), aligns reads to the genome, corrects Unique Molecular Identifiers (UMIs), generates a count matrix in a UMI-aware manner, calculates summary metrics for genes and cells, detects empty droplets, returns read outputs in BAM format, and returns cell gene counts in numpy matrix and h5ad file formats.\n</description>\n";
                    prompt += "\n";
                    prompt += "Pick a category from the subsequent list that describes the output format that the " + entryType + " supports.  Respond with the number of the category and do not include any additional information.\n";
                    prompt += "1. xml\n";
                    prompt += "2. json\n";
                    prompt += "3. bam\n";
                    prompt += "4. txt\n";
                    prompt += "5. fastq\n";
                    prompt += "6. none of the above\n";
                    LOG.info("PROMPT {}", prompt);
                    AIResponseInfo aiResponseInfo = aiModel.get().submitPrompt(prompt);
                    LOG.info("RESPONSE {}", aiResponseInfo.aiResponse());
                    String cleanedResponse = removeCategoryTagsFromResponse(aiResponseInfo.aiResponse());
                    aiResponseInfo = new AIResponseInfo(cleanedResponse, aiResponseInfo.isTruncated(), aiResponseInfo.inputTokens(), aiResponseInfo.outputTokens(), aiResponseInfo.cost(), aiResponseInfo.stopReason());
                    writeCategoryRecord(categoriesCsvPrinter, trsId, versionId, descriptorFile, aiResponseInfo);
                    LOG.info("Generated categories for entry with TRS ID {} and version {}", trsId, versionId);
                    numberOfCategoriesGenerated += 1;
                } catch (Exception ex) {
                    LOG.error("Unable to categorize entry with TRS ID {} and version {}, skipping", trsId, versionId, ex);
                    errorsCsvPrinter.printRecord(trsId, versionId, ex.getMessage());
                    numberOfFailures += 1;
                }
            }

            LOG.info("Generated categories for {} entries. Failed to categorize {} entries", numberOfCategoriesGenerated, numberOfFailures);
            logFile(numberOfCategoriesGenerated, categoriesFileName, "View generated categories in file " + categoriesFileName);
            logFile(numberOfFailures, errorsFileName, "View entries that failed categorization in file " + errorsFileName);
        } catch (IOException e) {
            exceptionMessage(e, "Unable to create new CSV output file", IO_ERROR);
        }
    }

    private List<TrsIdAndVersionId> getCategorizationCandidatesFromFile(String inputFileName) {
        List<TrsIdAndVersionId> candidates = new ArrayList<>();
        final Iterable<CSVRecord> entriesCsvRecords = readCsvFile(inputFileName, InputCsvHeaders.class);
        for (CSVRecord entry : entriesCsvRecords) {
            final String trsId = entry.get(InputCsvHeaders.trsId);
            final String versionId = entry.get(InputCsvHeaders.version);
            candidates.add(new TrsIdAndVersionId(trsId, versionId));
        }
        LOG.info("Retrieved {} categorization candidates from input file {}", candidates.size(), inputFileName);
        return candidates;
    }

    private List<TrsIdAndVersionId> getCategorizationCandidatesFromDockstore(ExtendedGa4GhApi extendedGa4GhApi, Integer maxCandidates) {
        final String dockstoreServerUrl = extendedGa4GhApi.getApiClient().getBasePath();
        List<TrsIdAndVersionId> candidates = new ArrayList<>();
        final int maxPaginationLimit = 1000;
        if (maxCandidates == null) {
            LOG.info("No maximum specified. Retrieving all categorization candidates from Dockstore {}", dockstoreServerUrl);
        } else if (maxCandidates > 0) {
            LOG.info("Retrieving a maximum of {} categorization candidates from Dockstore {}", maxCandidates, dockstoreServerUrl);
        } else {
            errorMessage("--max must be greater than 0", CLIENT_ERROR);
        }

        final int paginationLimit = Math.min(ObjectUtils.firstNonNull(maxCandidates, maxPaginationLimit), maxPaginationLimit);
        int pageNumber = 1;
        Integer totalCandidatesCount = null;
        while (maxCandidates == null || candidates.size() < maxCandidates) {
            final int offset = (pageNumber - 1) * paginationLimit;
            try {
                final List<TrsIdAndVersionId> candidatesFromDockstore = extendedGa4GhApi.getAITopicCandidates(offset, paginationLimit).stream()
                        .map(entryLiteAndVersionName -> new TrsIdAndVersionId(entryLiteAndVersionName.getEntryLite().getTrsId(), entryLiteAndVersionName.getVersionName()))
                        .toList();
                candidates.addAll(candidatesFromDockstore);
            } catch (ApiException exception) {
                exceptionMessage(exception, "Could not get categorization candidates from Dockstore", API_ERROR);
            }

            if (totalCandidatesCount == null) {
                try {
                    totalCandidatesCount = Integer.parseInt(
                            extendedGa4GhApi.getApiClient().getResponseHeaders().get("X-total-count").get(0));
                } catch (Exception exception) {
                    exceptionMessage(exception, "Could not get X-total-count header value for categorization candidates", API_ERROR);
                }
            }

            if (maxCandidates == null || maxCandidates > totalCandidatesCount) {
                maxCandidates = totalCandidatesCount;
            }
            pageNumber += 1;
        }

        LOG.info("Retrieved {} out of {} categorization candidates from {}", candidates.size(), totalCandidatesCount, dockstoreServerUrl);
        return candidates;
    }

    private Optional<BaseAIModel> getAiModel(AIModelType aiModelType) {
        if (aiModelType == AIModelType.CLAUDE_3_HAIKU || aiModelType == AIModelType.CLAUDE_3_5_SONNET || aiModelType == AIModelType.CLAUDE_4_5_HAIKU) {
            return Optional.of(new AnthropicClaudeModel(aiModelType));
        } else {
            return Optional.empty();
        }
    }

    private void writeCategorizationCandidates(List<TrsIdAndVersionId> candidates) {
        final String outputFileName = "categorization-candidates_" + Instant.now().truncatedTo(ChronoUnit.SECONDS).toString().replace("-", "").replace(":", "") + ".csv";
        try (CSVPrinter csvPrinter = new CSVPrinter(new FileWriter(outputFileName, StandardCharsets.UTF_8), CSVFormat.DEFAULT.builder().setHeader(InputCsvHeaders.class).build())) {
            for (TrsIdAndVersionId candidate : candidates) {
                csvPrinter.printRecord(candidate.trsId(), candidate.versionId());
            }
        } catch (IOException e) {
            exceptionMessage(e, "Unable to create new CSV output file", IO_ERROR);
        }
        LOG.info("View the categorization candidates in file {}", outputFileName);
    }

    private FileWrapper getDescriptorFile(Ga4Ghv20Api ga4Ghv20Api, String trsId, String versionId, List<DescriptorTypeEnum> descriptorTypes) throws ApiException {
        FileWrapper descriptorFile = null;
        for (int i = 0; i < descriptorTypes.size(); ++i) {
            DescriptorTypeEnum descriptorType = descriptorTypes.get(i);
            try {
                descriptorFile = ga4Ghv20Api.toolsIdVersionsVersionIdTypeDescriptorGet(trsId, descriptorType.toString(), versionId);
            } catch (ApiException ex) {
                if (i == descriptorTypes.size() - 1) {
                    throw ex;
                }
                continue;
            }

            if (descriptorType == DescriptorTypeEnum.NFL) {
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
            LOG.error("Could not grab config", e);
            return Optional.empty();
        }
        try {
            return Optional.of(ga4Ghv20Api.toolsIdVersionsVersionIdTypeDescriptorRelativePathGet(trsId, descriptorType.toString(), versionId, mainScriptPath));
        } catch (ApiException exception) {
            LOG.error("Could not get Nextflow main script {}", mainScriptPath, exception);
            return Optional.empty();
        }
    }

    private void uploadCategories(CategorizerConfig categorizerConfig, UploadCategoriesCommand uploadCategoriesCommand) {
        // TODO: set up extendedGa4GhApi and call the Dockstore API to upload categories once the endpoint is available
        final Iterable<CSVRecord> entriesWithCategories;

        LOG.info("Reading file {}", uploadCategoriesCommand.getCategoriesCsvFilePath());
        if (uploadCategoriesCommand.getCategoriesCsvFilePath().startsWith("s3://")) {
            entriesWithCategories = readS3CsvFile(uploadCategoriesCommand.getCategoriesCsvFilePath());
        } else {
            entriesWithCategories = readCsvFile(uploadCategoriesCommand.getCategoriesCsvFilePath(), OutputCsvHeaders.class);
        }
        int numberOfCategoriesUploaded = 0;
        int numberOfCategoriesSkippedUpload = 0;
        final Scanner scanner = new Scanner(System.in);

        for (CSVRecord entryWithCategories : entriesWithCategories) {
            final String trsId = entryWithCategories.get(OutputCsvHeaders.trsId);
            final String categories = entryWithCategories.get(OutputCsvHeaders.categories);
            final String version = entryWithCategories.get(OutputCsvHeaders.version);

            if (uploadCategoriesCommand.isReview()) {
                System.out.printf("%nReview the following categories for TRS ID %s and version %s:%n", trsId, version);
                System.out.printf("%s%n%n", categories);
                String approved = null;

                while (!"y".equals(approved) && !"n".equals(approved)) {
                    if (approved != null) {
                        System.out.print("Invalid response. ");
                    }
                    System.out.print("Do you approve the categories for upload to Dockstore? y/n [enter]: ");
                    approved = scanner.nextLine().trim();
                }

                if ("n".equals(approved)) {
                    LOG.info("Skipping categories upload for {}", trsId);
                    numberOfCategoriesSkippedUpload += 1;
                    continue;
                }
            }

            // TODO: replace with actual Dockstore API call to upload categories once the API endpoint is available
            LOG.info("Uploaded categories for {} (not yet implemented)", trsId);
            numberOfCategoriesUploaded += 1;
        }
        LOG.info("Uploaded categories for {} entries. Skipped upload for {} entries", numberOfCategoriesUploaded, numberOfCategoriesSkippedUpload);
    }

    /**
     * Logs the file name if the number of results is greater than 0. Otherwise deletes the file.
     */
    private void logFile(int numberOfResults, String resultsFileName, String logMessage) {
        if (numberOfResults == 0) {
            FileUtils.deleteQuietly(FileUtils.getFile(resultsFileName));
        } else {
            LOG.info("{}", logMessage);
        }
    }

    public static String removeCategoryTagsFromResponse(String aiResponse) {
        String cleaned = StringUtils.removeStart(aiResponse, "<categories>");
        return StringUtils.removeEnd(cleaned, "</categories>");
    }

    private static Ontology readOntology(String fileName) throws IOException {
    }

    private static CSVPrinter createCsvPrinter(String fileName, Class<? extends Enum<?>> csvHeaders) throws IOException {
        return new CSVPrinter(new FileWriter(fileName, StandardCharsets.UTF_8), CSVFormat.DEFAULT.builder().setHeader(csvHeaders).build());
    }

    private static Iterable<CSVRecord> readCsvFile(String inputCsvFilePath, Class<? extends Enum<?>> csvHeaders) {
        Iterable<CSVRecord> csvRecords = null;
        try {
            final Reader reader = new FileReader(inputCsvFilePath);
            csvRecords = parseCsvRecords(reader, csvHeaders);
        } catch (IOException e) {
            exceptionMessage(e, "Unable to read input CSV file", IO_ERROR);
        }
        return csvRecords;
    }

    private static Iterable<CSVRecord> readS3CsvFile(String s3FileUri) {
        final software.amazon.awssdk.services.s3.S3Client s3Client = S3ClientHelper.getS3Client();
        final String s3FileKey = s3FileUri.replace("s3://", "");
        final List<String> s3FileKeyComponents = List.of(s3FileKey.split("/"));
        if (s3FileKeyComponents.size() < 2) {
            errorMessage("Invalid S3 URI", IO_ERROR);
        }
        final String bucketName = s3FileKeyComponents.get(0);
        final String fileKey = String.join("/", s3FileKeyComponents.subList(1, s3FileKeyComponents.size()));
        final software.amazon.awssdk.services.s3.model.GetObjectRequest getObjectRequest =
                software.amazon.awssdk.services.s3.model.GetObjectRequest.builder()
                        .bucket(bucketName)
                        .key(fileKey)
                        .build();
        final software.amazon.awssdk.core.ResponseInputStream<software.amazon.awssdk.services.s3.model.GetObjectResponse> getObjectResponse =
                s3Client.getObject(getObjectRequest);
        final InputStreamReader streamReader = new InputStreamReader(getObjectResponse, StandardCharsets.UTF_8);
        return parseCsvRecords(streamReader, OutputCsvHeaders.class);
    }

    private static Iterable<CSVRecord> parseCsvRecords(Reader reader, Class<? extends Enum<?>> csvHeaders) {
        Iterable<CSVRecord> csvRecords = null;
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
                .setHeader(csvHeaders)
                .setSkipHeaderRecord(true)
                .setTrim(true)
                .build();
        try {
            csvRecords = csvFormat.parse(reader);
        } catch (IOException e) {
            exceptionMessage(e, "Unable to read input CSV file", IO_ERROR);
        }
        return csvRecords;
    }

    private static void writeCategoryRecord(CSVPrinter csvPrinter, String trsId, String versionId, FileWrapper descriptorFile, AIResponseInfo aiResponseInfo) {
        String descriptorChecksum = descriptorFile.getChecksum().isEmpty() ? "" : descriptorFile.getChecksum().get(0).getChecksum();
        try {
            csvPrinter.printRecord(trsId, versionId, descriptorFile.getUrl(), descriptorChecksum, aiResponseInfo.isTruncated(), aiResponseInfo.inputTokens(), aiResponseInfo.outputTokens(), aiResponseInfo.cost(), aiResponseInfo.stopReason(), aiResponseInfo.aiResponse());
        } catch (IOException e) {
            LOG.error("Unable to write CSV record to file, skipping", e);
        }
    }

    public record TrsIdAndVersionId(String trsId, String versionId) {
    }
}

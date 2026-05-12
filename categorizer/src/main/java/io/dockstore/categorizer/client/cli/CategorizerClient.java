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
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.dockstore.categorizer.Ontology;
import io.dockstore.categorizer.client.cli.CategorizerCommandLineArgs.CategorizeEntriesCommand;
import io.dockstore.categorizer.client.cli.CategorizerCommandLineArgs.CategorizeEntriesCommand.ErrorsCsvHeaders;
import io.dockstore.categorizer.client.cli.CategorizerCommandLineArgs.CategorizeEntriesCommand.InputCsvHeaders;
import io.dockstore.categorizer.client.cli.CategorizerCommandLineArgs.CategorizeEntriesCommand.OutputCsvHeaders;
import io.dockstore.categorizer.client.cli.CategorizerCommandLineArgs.ListStaleEntriesCommand;
import io.dockstore.categorizer.client.cli.CategorizerCommandLineArgs.PopulateCategoriesCommand;
import io.dockstore.common.NextflowUtilities;
import io.dockstore.common.NextflowUtilities.NextflowParsingException;
import io.dockstore.common.S3ClientHelper;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.ApiException;
import io.dockstore.openapi.client.api.EntriesApi;
import io.dockstore.openapi.client.api.ExtendedGa4GhApi;
import io.dockstore.openapi.client.api.Ga4Ghv20Api;
import io.dockstore.openapi.client.model.FileWrapper;
import io.dockstore.openapi.client.model.Tool;
import io.dockstore.openapi.client.model.ToolVersion;
import io.dockstore.openapi.client.model.ToolVersion.DescriptorTypeEnum;
import io.dockstore.utils.ai.AIModel;
import io.dockstore.utils.ai.AIModel.AIResponseInfo;
import io.dockstore.utils.ai.AIModelFactory;
import io.dockstore.utils.ai.AIModelType;
import io.dockstore.utils.ai.LoggingAIModel;
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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
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
        final ListStaleEntriesCommand listStaleEntriesCommand = new ListStaleEntriesCommand();
        final CategorizeEntriesCommand categorizeEntriesCommand = new CategorizeEntriesCommand();
        final PopulateCategoriesCommand populateCategoriesCommand = new PopulateCategoriesCommand();
        // TODO: add rest of commands
        jCommander.addCommand(categorizeEntriesCommand);
        jCommander.addCommand(listStaleEntriesCommand);
        jCommander.addCommand(populateCategoriesCommand);

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
            case "list-stale-entries" -> categorizerClient.listStaleEntries(categorizerConfig, listStaleEntriesCommand);
            case "categorize-entries" -> categorizerClient.categorizeEntries(categorizerConfig, categorizeEntriesCommand);
            case "populate-categories" -> categorizerClient.populateCategories(categorizerConfig, populateCategoriesCommand);
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
        final List<String> ontologyPaths = categorizeEntriesCommand.getOntologyJsonPaths();
        final Ontology ontology = combineOntologies(ontologyPaths.stream().map(CategorizerClient::readOntology).toList());
        final AIModelType aiModelType = categorizeEntriesCommand.getAiModel();
        final String inputFileName = categorizeEntriesCommand.getEntriesCsvFilePath();

        List<TrsIdAndVersionId> categorizationCandidates = getCategorizationCandidatesFromFile(inputFileName);

        AIModel aiModel = new LoggingAIModel(AIModelFactory.createModel(aiModelType));
        LOG.info("Categorizing entries using AI model {}", aiModelType.getModelId());
        final String outputFileNameSuffix = "_" + aiModelType + "_" + Instant.now().truncatedTo(ChronoUnit.SECONDS).toString().replace("-", "").replace(":", "") + ".csv";

        final List<OntologyHandler> ontologyHandlers = List.of(
                new InputDataOntologyHandler(),
                new InputFormatOntologyHandler(),
                new OutputDataOntologyHandler(),
                new OutputFormatOntologyHandler(),
                new OperationOntologyHandler(),
                new TopicOntologyHandler());

        checkOverlappingHandlers(ontologyHandlers, ontology);

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

                // TODO: move most of the primary descriptor retrieval code to a helper method in utils
                // Get required information to create a prompt
                final String entryType;
                final FileWrapper descriptorFile;
                final String description;
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
                    description = tool.getDescription();
                } catch (ApiException ex) {
                    LOG.error("Failed to get information for categorization candidate with TRS ID {} and version {} from Dockstore, skipping", trsId, versionId, ex);
                    errorsCsvPrinter.printRecord(trsId, versionId, ex.getMessage().replace("\n", " "));
                    numberOfFailures += 1;
                    continue;
                }

                // Classify into the ontology using AI model
                try {
                    EntryData entryData = new EntryData(entryType, trsId, description, descriptorFile.getContent());
                    List<Ontology.Node> allMatchingNodes = new ArrayList<>();
                    // For each Ontology handler, determine the nodes it handles and classify into them.
                    for (OntologyHandler handler : ontologyHandlers) {
                        List<Ontology.Node> candidateNodes = handler.handlesNodes(ontology).stream().filter(Ontology.Node::recommendedForAnnotation).toList();
                        if (candidateNodes.isEmpty()) {
                            continue;
                        }
                        LOG.info("{} handles {} nodes", handler, candidateNodes.size());
                        List<Ontology.Node> matchingNodes = handler.categorizeIntoNodes(candidateNodes, entryData, aiModel);
                        allMatchingNodes.addAll(matchingNodes);
                    }
                    output(trsId, versionId, allMatchingNodes);
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

    private void checkOverlappingHandlers(List<OntologyHandler> handlers, Ontology ontology) {
        List<String> ids = handlers.stream().flatMap(h -> h.handlesNodes(ontology).stream().map(Ontology.Node::id)).toList();
        if (ids.size() != new HashSet<>(ids).size()) {
            errorMessage("Some ontology nodes are handled by multiple handlers.", GENERIC_ERROR);
        }
    }

    private void output(String trsId, String versionId, List<Ontology.Node> nodes) {
        String dockstoreUrl = "https://dockstore.org/workflows/%s:%s".formatted(trsId.substring(trsId.indexOf("github.com")), versionId);
        System.out.println("* [%s](%s)".formatted(dockstoreUrl, dockstoreUrl));
        System.out.println(nodes.stream().map(node ->
                "    * [%s](%s)".formatted(node.label(), node.source())
            ).collect(Collectors.joining("\n")));
        // TODO: write results to CSV
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

    private void listStaleEntries(CategorizerConfig categorizerConfig, ListStaleEntriesCommand listStaleEntriesCommand) {
        final ApiClient apiClient = setupApiClient(categorizerConfig.dockstoreServerUrl(), categorizerConfig.dockstoreToken());
        final EntriesApi entriesApi = new EntriesApi(apiClient);
        final List<TrsIdAndVersionId> staleEntries = getStaleEntriesFromDockstore(entriesApi, listStaleEntriesCommand.getIntervalSeconds(), listStaleEntriesCommand.getMax());
        if (staleEntries.isEmpty()) {
            LOG.info("No stale entries found");
            return;
        }
        writeCategorizationCandidates(staleEntries);
    }

    private List<TrsIdAndVersionId> getStaleEntriesFromDockstore(EntriesApi entriesApi, long intervalSeconds, Integer maxEntries) {
        final String dockstoreServerUrl = entriesApi.getApiClient().getBasePath();
        List<TrsIdAndVersionId> staleEntries = new ArrayList<>();
        final int maxPaginationLimit = 1000;
        if (maxEntries == null) {
            LOG.info("No maximum specified. Retrieving all stale entries from Dockstore {}", dockstoreServerUrl);
        } else if (maxEntries > 0) {
            LOG.info("Retrieving a maximum of {} stale entries from Dockstore {}", maxEntries, dockstoreServerUrl);
        } else {
            errorMessage("--max must be greater than 0", CLIENT_ERROR);
        }

        final int paginationLimit = Math.min(ObjectUtils.firstNonNull(maxEntries, maxPaginationLimit), maxPaginationLimit);
        int pageNumber = 1;
        Integer totalStaleEntriesCount = null;
        while (maxEntries == null || staleEntries.size() < maxEntries) {
            final int offset = (pageNumber - 1) * paginationLimit;
            try {
                final List<TrsIdAndVersionId> staleEntriesFromDockstore = entriesApi.findEntriesToCategorize(intervalSeconds, offset, paginationLimit).stream()
                        .map(entryLiteAndVersionName -> new TrsIdAndVersionId(entryLiteAndVersionName.getEntryLite().getTrsId(), entryLiteAndVersionName.getVersionName()))
                        .toList();
                staleEntries.addAll(staleEntriesFromDockstore);
            } catch (ApiException exception) {
                exceptionMessage(exception, "Could not get stale entries from Dockstore", API_ERROR);
            }

            if (totalStaleEntriesCount == null) {
                try {
                    totalStaleEntriesCount = Integer.parseInt(
                            entriesApi.getApiClient().getResponseHeaders().get("X-total-count").get(0));
                } catch (Exception exception) {
                    exceptionMessage(exception, "Could not get X-total-count header value for stale entries", API_ERROR);
                }
            }

            if (maxEntries == null || maxEntries > totalStaleEntriesCount) {
                maxEntries = totalStaleEntriesCount;
            }
            pageNumber += 1;
        }

        LOG.info("Retrieved {} out of {} stale entries from {}", staleEntries.size(), totalStaleEntriesCount, dockstoreServerUrl);
        return staleEntries;
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

    private void populateCategories(CategorizerConfig categorizerConfig, PopulateCategoriesCommand populateCategoriesCommand) {
        // TODO: set up extendedGa4GhApi and call the Dockstore API to upload categories once the endpoint is available
        final Iterable<CSVRecord> entriesWithCategories;

        LOG.info("Reading file {}", populateCategoriesCommand.getCategoriesCsvFilePath());
        if (populateCategoriesCommand.getCategoriesCsvFilePath().startsWith("s3://")) {
            entriesWithCategories = readS3CsvFile(populateCategoriesCommand.getCategoriesCsvFilePath());
        } else {
            entriesWithCategories = readCsvFile(populateCategoriesCommand.getCategoriesCsvFilePath(), OutputCsvHeaders.class);
        }
        int numberOfCategoriesPopulated = 0;
        int numberOfCategoriesSkippedPopulation = 0;

        for (CSVRecord entryWithCategories : entriesWithCategories) {
            final String trsId = entryWithCategories.get(OutputCsvHeaders.trsId);
            final String version = entryWithCategories.get(OutputCsvHeaders.version);
            final String categoryId = entryWithCategories.get(OutputCsvHeaders.categoryId);
            final boolean isMember = Boolean.parseBoolean(entryWithCategories.get(OutputCsvHeaders.isMember));

            // TODO: replace with actual Dockstore API call + logic to populate categories.
            LOG.info("Populated categories for {} (not yet implemented)", trsId);
            numberOfCategoriesPopulated += 1;
        }
        LOG.info("Populated categories for {} entries. Skipped upload for {} entries", numberOfCategoriesPopulated, numberOfCategoriesSkippedPopulation);
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

    private static Ontology combineOntologies(List<Ontology> ontologies) {
        Ontology combined = new Ontology();
        for (Ontology ontology : ontologies) {
            for (Ontology.Node node : ontology.getNodes()) {
                combined.addNode(node.id(), node.label(), node.definition(), node.parentIds(), node.source(), node.recommendedForAnnotation());
            }
        }
        return combined;
    }

    private static Ontology readOntology(String fileName) {
        try (Reader reader = new FileReader(fileName, StandardCharsets.UTF_8)) {
            JsonArray jsonArray = JsonParser.parseReader(reader).getAsJsonArray();
            Ontology ontology = new Ontology();
            for (JsonElement element : jsonArray) {
                JsonObject obj = element.getAsJsonObject();
                String id = obj.get("id").getAsString();
                String label = obj.get("label").getAsString();
                String definition = obj.get("definition").getAsString();
                String source = obj.get("source").getAsString();
                boolean recommendedForAnnotation = obj.get("recommended_for_annotation").getAsBoolean();
                List<String> parentIds = new ArrayList<>();
                for (JsonElement parent : obj.get("parent_ids").getAsJsonArray()) {
                    parentIds.add(parent.getAsString());
                }
                ontology.addNode(id, label, definition, parentIds, source, recommendedForAnnotation);
            }
            return ontology;
        } catch (IOException e) {
            exceptionMessage(e, "Unable to read ontology file", IO_ERROR);
            throw new RuntimeException("aborting");
        }
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

package io.dockstore.categorizer.client.cli;

import static io.dockstore.utils.ConfigFileUtils.getConfiguration;
import static io.dockstore.utils.DockstoreApiClientUtils.setupApiClient;
import static io.dockstore.utils.ExceptionHandler.API_ERROR;
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
import io.dockstore.categorizer.client.cli.CategorizerCommandLineArgs.CategorizeEntriesCommand.CategorizationCsvHeaders;
import io.dockstore.categorizer.client.cli.CategorizerCommandLineArgs.CategorizeEntriesCommand.EntryCsvHeaders;
import io.dockstore.categorizer.client.cli.CategorizerCommandLineArgs.CategorizeEntriesCommand.ErrorsCsvHeaders;
import io.dockstore.categorizer.client.cli.CategorizerCommandLineArgs.CreateCategoriesCommand;
import io.dockstore.categorizer.client.cli.CategorizerCommandLineArgs.DeleteCategoriesCommand;
import io.dockstore.categorizer.client.cli.CategorizerCommandLineArgs.ListAllEntriesCommand;
import io.dockstore.categorizer.client.cli.CategorizerCommandLineArgs.ListStaleEntriesCommand;
import io.dockstore.categorizer.client.cli.CategorizerCommandLineArgs.PopulateCategoriesCommand;
import io.dockstore.common.S3ClientHelper;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.ApiException;
import io.dockstore.openapi.client.api.EntriesApi;
import io.dockstore.openapi.client.api.ExtendedGa4GhApi;
import io.dockstore.openapi.client.api.Ga4Ghv20Api;
import io.dockstore.openapi.client.model.EntryLiteAndVersionName;
import io.dockstore.openapi.client.model.FileWrapper;
import io.dockstore.openapi.client.model.Tool;
import io.dockstore.utils.EntryUtils;
import io.dockstore.utils.RetrievalUtils;
import io.dockstore.utils.ai.AIModel;
import io.dockstore.utils.ai.AIModel.AIResponseInfo;
import io.dockstore.utils.ai.AIModelFactory;
import io.dockstore.utils.ai.AIModelType;
import io.dockstore.utils.ai.LoggingAIModel;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
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
        final ListAllEntriesCommand listAllEntriesCommand = new ListAllEntriesCommand();
        final ListStaleEntriesCommand listStaleEntriesCommand = new ListStaleEntriesCommand();
        final CategorizeEntriesCommand categorizeEntriesCommand = new CategorizeEntriesCommand();
        final PopulateCategoriesCommand populateCategoriesCommand = new PopulateCategoriesCommand();
        final CreateCategoriesCommand createCategoriesCommand = new CreateCategoriesCommand();
        final DeleteCategoriesCommand deleteCategoriesCommand = new DeleteCategoriesCommand();
        jCommander.addCommand(categorizeEntriesCommand);
        jCommander.addCommand(listStaleEntriesCommand);
        jCommander.addCommand(populateCategoriesCommand);
        jCommander.addCommand(listAllEntriesCommand);
        jCommander.addCommand(createCategoriesCommand);
        jCommander.addCommand(deleteCategoriesCommand);

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
            case "list-all-entries" -> categorizerClient.listAllEntries(categorizerConfig, listAllEntriesCommand);
            case "list-stale-entries" -> categorizerClient.listStaleEntries(categorizerConfig, listStaleEntriesCommand);
            case "categorize-entries" -> categorizerClient.categorizeEntries(categorizerConfig, categorizeEntriesCommand);
            case "populate-categories" -> categorizerClient.populateCategories(categorizerConfig, populateCategoriesCommand);
            case "create-categories" -> categorizerClient.createCategories(categorizerConfig, createCategoriesCommand);
            case "delete-categories" -> categorizerClient.deleteCategories(categorizerConfig, deleteCategoriesCommand);
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

        final List<String> ontologyPaths = categorizeEntriesCommand.getOntologyJsonPaths();
        final Ontology ontology = readOntologies(ontologyPaths);

        final String entriesPath = categorizeEntriesCommand.getEntriesCsvFilePath();
        List<TrsIdAndVersion> entries = readEntries(entriesPath);

        AIModelType aiModelType = categorizeEntriesCommand.getAiModel();
        AIModel aiModel = new LoggingAIModel(AIModelFactory.createModel(aiModelType));
        LOG.info("Categorizing entries using AI model {}", aiModelType.getModelId());

        final List<OntologyHandler> ontologyHandlers = List.of(
            new OperationOntologyHandler(),
            new TopicOntologyHandler(),
            new InputFormatOntologyHandler(),
            new OutputFormatOntologyHandler(),
            new InputDataOntologyHandler(),
            new OutputDataOntologyHandler()
        );

        checkOverlappingHandlers(ontologyHandlers, ontology);

        final String errorsFileName = "errors";
        int numberOfFailures = 0;
        try (CSVPrinter categorizationsCsvPrinter = createCsvPrinter(new PrintWriter(System.out), CategorizationCsvHeaders.class);
                CSVPrinter errorsCsvPrinter = createCsvPrinter(errorsFileName, ErrorsCsvHeaders.class)) {
            for (TrsIdAndVersion entry: entries) {
                final String trsId = entry.trsId();
                final String versionId = entry.versionId();
                try {
                    // Retrieve data about the entry.
                    final EntryData entryData = retrieveEntryData(apiClient, trsId, versionId);
                    // For each Ontology handler, determine the nodes it covers and classify into the nodes that are recommended for annotation.
                    for (OntologyHandler handler : ontologyHandlers) {
                        List<Ontology.Node> coveredNodes = handler.handlesNodes(ontology);
                        List<Ontology.Node> candidateNodes = coveredNodes.stream().filter(Ontology.Node::recommendedForAnnotation).toList();
                        if (candidateNodes.isEmpty()) {
                            continue;
                        }
                        LOG.info("{} handles {} nodes", handler, candidateNodes.size());
                        List<Ontology.Node> matchingNodes = handler.categorizeIntoNodes(candidateNodes, entryData, aiModel);
                        for (Ontology.Node matchingNode: matchingNodes) {
                            categorizationsCsvPrinter.printRecord(entry.trsId(), entry.versionId(), matchingNode.id(), true);
                        }
                    }
                } catch (Exception ex) {
                    LOG.error("Unable to categorize entry with TRS ID {} and version {}, skipping", trsId, versionId, ex);
                    errorsCsvPrinter.printRecord(trsId, versionId, ex.getMessage());
                    numberOfFailures++;
                }
                // TODO: output matches to csv
            }

            LOG.info("Failed to categorize {} entries", numberOfFailures);
            /*
            logFile(numberOfCategoriesGenerated, categoriesFileName, "View generated categories in file " + categoriesFileName);
            logFile(numberOfFailures, errorsFileName, "View entries that failed categorization in file " + errorsFileName);
            */
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

    private void outputEntryAndVersion(String trsId, String versionId) {

        String dockstoreUrl = "https://dockstore.org/workflows/%s:%s".formatted(trsId.substring(trsId.indexOf("github.com")), versionId);
        System.out.println("MARKDOWN:* [%s](%s)".formatted(dockstoreUrl, dockstoreUrl));
    }

    private void outputMatchingCategories(OntologyHandler handler, List<Ontology.Node> nodes) {
        System.out.println("MARKDOWN:    * %s:".formatted(handler.getName()));
        System.out.println(nodes.stream().map(node ->
            "MARKDOWN:        * [%s](%s)".formatted(node.label(), node.source())
            ).collect(Collectors.joining("\n")));
    }

    private List<TrsIdAndVersion> readEntries(String path) {
        List<TrsIdAndVersion> entries = new ArrayList<>();
        final Iterable<CSVRecord> entriesCsvRecords = readCsvFile(path, EntryCsvHeaders.class);
        for (CSVRecord entry : entriesCsvRecords) {
            final String trsId = entry.get(EntryCsvHeaders.trsId);
            final String versionId = entry.get(EntryCsvHeaders.version);
            entries.add(new TrsIdAndVersion(trsId, versionId));
        }
        LOG.info("Read {} entries from input file {}", entries.size(), path);
        return entries;
    }

    private void listStaleEntries(CategorizerConfig categorizerConfig, ListStaleEntriesCommand listStaleEntriesCommand) {
        final ApiClient apiClient = setupApiClient(categorizerConfig.dockstoreServerUrl(), categorizerConfig.dockstoreToken());
        final EntriesApi entriesApi = new EntriesApi(apiClient);
        final int max = listStaleEntriesCommand.getMax();
        final long intervalSeconds = listStaleEntriesCommand.getIntervalSeconds();
        final List<TrsIdAndVersion> staleEntries = getStaleEntriesFromDockstore(entriesApi, intervalSeconds, max);
        writeEntries(staleEntries, new OutputStreamWriter(System.out, StandardCharsets.UTF_8));
    }

    private void listAllEntries(CategorizerConfig categorizerConfig, ListAllEntriesCommand listAllEntriesCommand) {
        final ApiClient apiClient = setupApiClient(categorizerConfig.dockstoreServerUrl(), categorizerConfig.dockstoreToken());
        final ExtendedGa4GhApi extendedGa4GhApi = new ExtendedGa4GhApi(apiClient);
        final int max = listAllEntriesCommand.getMax();
        List<TrsIdAndVersion> allEntries = getAllEntriesFromDockstore(extendedGa4GhApi, max);
        writeEntries(allEntries, new OutputStreamWriter(System.out, StandardCharsets.UTF_8));
    }

    private List<TrsIdAndVersion> getStaleEntriesFromDockstore(EntriesApi entriesApi, long intervalSeconds, int maxEntries) {
        List<EntryLiteAndVersionName> entries = RetrievalUtils.pagedRetrieval((offset, limit) -> {
            try {
                return entriesApi.findEntriesToCategorize(intervalSeconds, offset, limit);
            } catch (ApiException exception) {
                exceptionMessage(exception, "Could not get stale entries from Dockstore", API_ERROR);
                return List.of();
            }
        }, maxEntries);
        LOG.info("Retrieved {} stale entries", entries.size());
        return entries.stream().map(this::convertEntry).toList();
    }

    private List<TrsIdAndVersion> getAllEntriesFromDockstore(ExtendedGa4GhApi extendedGa4GhApi, int maxEntries) {
        List<EntryLiteAndVersionName> entries = RetrievalUtils.pagedRetrieval((offset, limit) -> {
            try {
                return extendedGa4GhApi.getAllEntries(offset, limit);
            } catch (ApiException exception) {
                exceptionMessage(exception, "Could not get entries from Dockstore", API_ERROR);
                return List.of();
            }
        }, maxEntries);
        LOG.info("Retrieved {} entries", entries.size());
        return entries.stream().map(this::convertEntry).toList();
    }

    private TrsIdAndVersion convertEntry(EntryLiteAndVersionName e) {
        return new TrsIdAndVersion(e.getEntryLite().getTrsId(), e.getVersionName());
    }

    private void writeEntries(List<TrsIdAndVersion> candidates, Writer writer) {
        try {
            CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT.builder().setHeader(EntryCsvHeaders.class).build());
            for (TrsIdAndVersion candidate : candidates) {
                csvPrinter.printRecord(candidate.trsId(), candidate.versionId());
            }
            writer.flush();
        } catch (IOException e) {
            exceptionMessage(e, "Unable to write CSV output", IO_ERROR);
        }
    }

    private EntryData retrieveEntryData(ApiClient apiClient, String trsId, String versionId) throws ApiException {
        final Tool tool = new Ga4Ghv20Api(apiClient).toolsIdGet(trsId);
        final String entryType = tool.getToolclass().getName().toLowerCase();
        final String description = tool.getDescription();
        final Optional<FileWrapper> primaryDescriptor = EntryUtils.retrievePrimaryDescriptor(apiClient, trsId, versionId);
        if (primaryDescriptor.isEmpty()) {
            throw new RuntimeException("Could not retrieve version");
        }
        return new EntryData(entryType, trsId, description, primaryDescriptor.get().getContent());
    }

    private void populateCategories(CategorizerConfig categorizerConfig, PopulateCategoriesCommand populateCategoriesCommand) {
        // TODO: set up extendedGa4GhApi and call the Dockstore API to upload categories once the endpoint is available
        final Iterable<CSVRecord> categorizations;

        String path = populateCategoriesCommand.getCategorizationsCsvPath();
        LOG.info("Reading file {}", path);
        if (path.startsWith("s3://")) {
            categorizations = readS3CsvFile(path);
        } else {
            categorizations = readCsvFile(path, CategorizationCsvHeaders.class);
        }

        for (CSVRecord categorization: categorizations) {
            final String trsId = categorization.get(CategorizationCsvHeaders.trsId);
            final String version = categorization.get(CategorizationCsvHeaders.version);
            final String categoryId = categorization.get(CategorizationCsvHeaders.categoryId);
            final boolean isMember = Boolean.parseBoolean(categorization.get(CategorizationCsvHeaders.isMember));

            // TODO: replace with actual Dockstore API call + logic to populate categories.
        }
    }

    private void createCategories(CategorizerConfig categorizerConfig, CreateCategoriesCommand createCategoriesCommand) {
        // TODO: implement
        LOG.info("create-categories is not yet implemented");
    }

    private void deleteCategories(CategorizerConfig categorizerConfig, DeleteCategoriesCommand deleteCategoriesCommand) {
        // TODO: call yet-to-be-implemented webservice endpoint to delete categories whose IDs match the regexp
        LOG.info("delete-categories is not yet implemented");
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

    private static Ontology readOntology(String path) {
        try (Reader reader = new FileReader(path, StandardCharsets.UTF_8)) {
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

    private static Ontology readOntologies(List<String> paths) {
        return combineOntologies(paths.stream().map(CategorizerClient::readOntology).toList());
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

    private static CSVPrinter createCsvPrinter(String fileName, Class<? extends Enum<?>> csvHeaders) throws IOException {
        return createCsvPrinter(new FileWriter(fileName, StandardCharsets.UTF_8), csvHeaders);
    }

    private static CSVPrinter createCsvPrinter(Writer writer, Class<? extends Enum<?>> csvHeaders) throws IOException {
        return new CSVPrinter(writer, CSVFormat.DEFAULT.builder().setHeader(csvHeaders).build());
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
        return parseCsvRecords(streamReader, CategorizationCsvHeaders.class);
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

    public record TrsIdAndVersion(String trsId, String versionId) {
    }
}

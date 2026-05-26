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
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.dockstore.categorizer.Ontology;
import io.dockstore.categorizer.client.cli.CategorizerCommandLineArgs.CategorizeEntriesCommand;
import io.dockstore.categorizer.client.cli.CategorizerCommandLineArgs.CreateCategoriesCommand;
import io.dockstore.categorizer.client.cli.CategorizerCommandLineArgs.DeleteCategoriesCommand;
import io.dockstore.categorizer.client.cli.CategorizerCommandLineArgs.ListAllEntriesCommand;
import io.dockstore.categorizer.client.cli.CategorizerCommandLineArgs.ListCategoriesCommand;
import io.dockstore.categorizer.client.cli.CategorizerCommandLineArgs.ListStaleEntriesCommand;
import io.dockstore.categorizer.client.cli.CategorizerCommandLineArgs.PopulateCategoriesCommand;
import io.dockstore.openapi.client.ApiClient;
import io.dockstore.openapi.client.ApiException;
import io.dockstore.openapi.client.api.EntriesApi;
import io.dockstore.openapi.client.api.ExtendedGa4GhApi;
import io.dockstore.openapi.client.api.Ga4Ghv20Api;
import io.dockstore.openapi.client.api.OrganizationsApi;
import io.dockstore.openapi.client.api.WorkflowsApi;
import io.dockstore.openapi.client.model.Collection;
import io.dockstore.openapi.client.model.Entry;
import io.dockstore.openapi.client.model.EntryLiteAndVersionName;
import io.dockstore.openapi.client.model.FileWrapper;
import io.dockstore.openapi.client.model.Organization;
import io.dockstore.openapi.client.model.Tool;
import io.dockstore.utils.CsvReader;
import io.dockstore.utils.CsvWriter;
import io.dockstore.utils.EntryUtils;
import io.dockstore.utils.IOUtils;
import io.dockstore.utils.RetrievalUtils;
import io.dockstore.utils.ai.AIModel;
import io.dockstore.utils.ai.AIModelFactory;
import io.dockstore.utils.ai.AIModelType;
import io.dockstore.utils.ai.LoggingAIModel;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CLI entry point for the Dockstore categorizer tool. Parses command-line arguments via JCommander
 * and dispatches to one of several commands: listing all or stale entries, AI-driven categorization
 * of entries against ontology nodes, creating/populating/listing/deleting Dockstore categories
 * in the "ai" organization that represent the nodes of the backing ontologies.
 */
public class CategorizerClient {
    private static final Logger LOG = LoggerFactory.getLogger(CategorizerClient.class);
    private static final String AI_ORGANIZATION_NAME = "dockstoreai";

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
        final ListCategoriesCommand listCategoriesCommand = new ListCategoriesCommand();
        final DeleteCategoriesCommand deleteCategoriesCommand = new DeleteCategoriesCommand();
        jCommander.addCommand(listAllEntriesCommand);
        jCommander.addCommand(listStaleEntriesCommand);
        jCommander.addCommand(categorizeEntriesCommand);
        jCommander.addCommand(populateCategoriesCommand);
        jCommander.addCommand(createCategoriesCommand);
        jCommander.addCommand(listCategoriesCommand);
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
            case "list-categories" -> categorizerClient.listCategories(categorizerConfig, listCategoriesCommand);
            case "delete-categories" -> categorizerClient.deleteCategories(categorizerConfig, deleteCategoriesCommand);
            default -> errorMessage("Unknown command", GENERIC_ERROR);
            }
        }

        if (jCommander.getParsedCommand() != null) {
            final Instant endTime = Instant.now();
            LOG.info("{} took {}", jCommander.getParsedCommand(), Duration.between(startTime, endTime));
        }
    }

    private void listAllEntries(CategorizerConfig categorizerConfig, ListAllEntriesCommand listAllEntriesCommand) {
        final ApiClient apiClient = setupApiClient(categorizerConfig.dockstoreServerUrl(), categorizerConfig.dockstoreToken());
        final ExtendedGa4GhApi extendedGa4GhApi = new ExtendedGa4GhApi(apiClient);
        final int max = listAllEntriesCommand.getMax();
        List<TrsIdAndVersion> allEntries = getAllEntriesFromDockstore(extendedGa4GhApi, max);
        writeCsvToStdout(allEntries, TrsIdAndVersion.class);
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

    private void listStaleEntries(CategorizerConfig categorizerConfig, ListStaleEntriesCommand listStaleEntriesCommand) {
        final ApiClient apiClient = setupApiClient(categorizerConfig.dockstoreServerUrl(), categorizerConfig.dockstoreToken());
        final EntriesApi entriesApi = new EntriesApi(apiClient);
        final int max = listStaleEntriesCommand.getMax();
        final long intervalSeconds = listStaleEntriesCommand.getIntervalSeconds();
        final List<TrsIdAndVersion> staleEntries = getStaleEntriesFromDockstore(entriesApi, intervalSeconds, max);
        writeCsvToStdout(staleEntries, TrsIdAndVersion.class);
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

    private TrsIdAndVersion convertEntry(EntryLiteAndVersionName e) {
        return new TrsIdAndVersion(e.getEntryLite().getTrsId(), e.getVersionName());
    }

    private <T> void writeCsvToStdout(Iterable<T> items, Class<T> pojoClass) {
        try (Writer writer = stdoutWriter(); CsvWriter<T> csvWriter = new CsvWriter<>(writer, pojoClass)) {
            csvWriter.writeAll(items);
        } catch (IOException e) {
            exceptionMessage(e, "Unable to write CSV output", IO_ERROR);
        }
    }

    private Writer stdoutWriter() {
        return new OutputStreamWriter(System.out, StandardCharsets.UTF_8);
    }

    /**
     * Categorizes the specified Dockstore entries by retrieving entry information and using AI to classify into the specified ontologies.
     * @param categorizerConfig
     * @param categorizeEntriesCommand
     */
    private void categorizeEntries(CategorizerConfig categorizerConfig, CategorizeEntriesCommand categorizeEntriesCommand) {
        final String dockstoreServerUrl = categorizerConfig.dockstoreServerUrl();
        final ApiClient apiClient = setupApiClient(dockstoreServerUrl, categorizerConfig.dockstoreToken());

        final List<String> ontologyPaths = categorizeEntriesCommand.getOntologyJsonPaths();
        final Ontology ontology = readOntologies(ontologyPaths);

        final String entriesPath = categorizeEntriesCommand.getEntriesCsvFilePath();
        List<TrsIdAndVersion> entries = readCsv(entriesPath, TrsIdAndVersion.class);
        LOG.info("Read {} entries from input file {}", entries.size(), entriesPath);

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

        int numberOfFailures = 0;
        try (Writer writer = stdoutWriter(); CsvWriter<Categorization> categorizationsWriter = new CsvWriter<>(writer, Categorization.class)) {
            for (TrsIdAndVersion entry: entries) {
                final String trsId = entry.trsId();
                final String version = entry.version();
                try {
                    // Retrieve data about the entry.
                    final EntryData entryData = retrieveEntryData(apiClient, trsId, version);
                    // For each ontology handler, categorize the entry into the appropriate nodes (categories).
                    for (OntologyHandler handler: ontologyHandlers) {
                        // Determine the "recommended for annotation" nodes that the handler covers.
                        List<Ontology.Node> coveredNodes = handler.coverage(ontology);
                        List<Ontology.Node> candidateNodes = coveredNodes.stream().filter(Ontology.Node::recommendedForAnnotation).toList();
                        if (candidateNodes.isEmpty()) {
                            continue;
                        }
                        LOG.info("{} handles {} nodes", handler, candidateNodes.size());
                        // Determine which nodes (categories) match the entry, and write the matching node information to the CSV.
                        List<Ontology.Node> matchingNodes = handler.categorize(candidateNodes, entryData, aiModel);
                        for (Ontology.Node matchingNode: matchingNodes) {
                            categorizationsWriter.write(new Categorization(entry.trsId(), entry.version(), matchingNode.id(), true));
                        }
                    }
                } catch (Exception ex) {
                    LOG.error("Unable to categorize entry with TRS ID {} and version {}, skipping", trsId, version, ex);
                    numberOfFailures++;
                }
            }
            LOG.info("Failed to categorize {} entries", numberOfFailures);
        } catch (IOException e) {
            exceptionMessage(e, "Unable to create new CSV output file", IO_ERROR);
        }
    }

    private static Ontology readOntologies(List<String> paths) {
        try {
            List<Ontology> ontologies = new ArrayList<>();
            for (String path : paths) {
                try (Reader reader = IOUtils.reader(path)) {
                    ontologies.add(Ontology.read(reader));
                }
            }
            return Ontology.combine(ontologies);
        } catch (IOException e) {
            exceptionMessage(e, "Unable to read ontology file", IO_ERROR);
            throw new RuntimeException("aborting");
        }
    }

    private void checkOverlappingHandlers(List<OntologyHandler> handlers, Ontology ontology) {
        // For each ontology handler, calculate the IDs of the recommended-for-annotation nodes that it covers.
        // Concatenate the IDs into a single list.
        List<String> ids = handlers.stream().flatMap(h -> h.coverage(ontology).stream().filter(Ontology.Node::recommendedForAnnotation).map(Ontology.Node::id)).toList();
        // If there are duplicate IDs, multiple Ontology handlers cover the same recommended-for-annotation node.
        if (ids.size() != new HashSet<>(ids).size()) {
            errorMessage("Multiple OntologyHandlers cover the same recommended-for-annotation node.", GENERIC_ERROR);
        }
    }

    private <T> List<T> readCsv(String path, Class<T> pojoClass) {
        try (Reader reader = IOUtils.reader(path); CsvReader<T> csvReader = new CsvReader<>(reader, pojoClass)) {
            return csvReader.readAll();
        } catch (IOException e) {
            exceptionMessage(e, "Unable to read CSV file: " + path, IO_ERROR);
            return List.of();
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
        String path = populateCategoriesCommand.getCategorizationsCsvPath();
        final List<Categorization> categorizations = readCsv(path, Categorization.class);

        final ApiClient apiClient = setupApiClient(categorizerConfig.dockstoreServerUrl(), categorizerConfig.dockstoreToken());
        final OrganizationsApi organizationsApi = new OrganizationsApi(apiClient);
        final WorkflowsApi workflowsApi = new WorkflowsApi(apiClient);

        final Organization organization = getAiOrganization(organizationsApi);

        // Map category IDs to the corresponding Dockstore Collections.
        // We'll use this later to avoid some redundant requests.
        final List<String> categoryIds = categorizations.stream().map(Categorization::categoryId).distinct().toList();
        final Map<String, Collection> categoryIdToCollection = new HashMap<>();
        for (String categoryId: categoryIds) {
            try {
                categoryIdToCollection.put(categoryId, organizationsApi.getCollectionByName(AI_ORGANIZATION_NAME, categoryId));
                LOG.info("Retrieved category '{}'", categoryId);
            } catch (ApiException e) {
                LOG.error("Unable to retrieve category '{}'", categoryId, e);
            }
        }

        // Map entry paths to the corresponding Dockstore Entries.
        // We'll use this later to avoid some redundant requests.
        final List<String> trsIds = categorizations.stream().map(Categorization::trsId).distinct().toList();
        final Map<String, Entry> trsIdToEntry = new HashMap<>();
        for (String trsId: trsIds) {
            try {
                trsIdToEntry.put(trsId, workflowsApi.getPublishedEntryByPath(trsIdToPath(trsId)));
                LOG.info("Retrieved entry '{}'", trsId);
            } catch (ApiException e) {
                LOG.error("Unable to retrieve entry '{}'", trsId, e);
            }
        }

        // For each Categorization, add or remove the entry from the category.
        for (Categorization categorization: categorizations) {
            final String trsId = categorization.trsId();
            final String categoryId = categorization.categoryId();
            final boolean isMember = categorization.isMember();

            if (!isMember) {
                LOG.info("Removing a member from a category is not yet supported, skipping entry {} from category {}", trsId, categoryId);
                continue;
            }

            final Collection collection = categoryIdToCollection.get(categoryId);
            if (collection == null) {
                LOG.info("No corresponding category '{}'", categoryId);
                continue;
            }
            final Entry entry = trsIdToEntry.get(trsId);
            if (entry == null) {
                LOG.info("No corresponding entry '{}'", trsId);
                continue;
            }

            // TODO: add logic to confirm that a human has not removed the entry from the category.  In such case, we won't add.
            try {
                organizationsApi.addEntryToCollection(organization.getId(), collection.getId(), entry.getId(), null, false); // TODO: adjust to set the curator to "AI", once that functionality enters the lexicon
                LOG.info("Added entry {} to category {}", trsId, categoryId);
            } catch (ApiException e) {
                LOG.error("Unable to add entry {} to category {}", trsId, categoryId, e);
            }
        }

        // For each entry that was categorized and exists on the webservice, update the "time of last categorization".
        // It is probably good enough to use "now" as the time of last categorization, even though the actual categorization happened a bit earlier.
        final EntriesApi entriesApi = new EntriesApi(apiClient);
        final List<String> categorizedTrsIds = categorizations.stream().map(Categorization::trsId).distinct().toList();
        for (String trsId: categorizedTrsIds) {
            final Entry entry = trsIdToEntry.get(trsId);
            if (entry == null) {
                continue;
            }
            try {
                entriesApi.setLastCategorizedDate(entry.getId(), null, null);
                LOG.info("Updated time of last categorization for entry {}", trsId);
            } catch (ApiException e) {
                LOG.error("Unable to update time of last categorization for entry {}", trsId, e);
            }
        }

        // TODO: after the categories are populated, we need to reindex the involed entries in ES
    }

    private String trsIdToPath(String trsId) {
        final String workflowPrefix = "#workflow/";
        if (trsId.startsWith(workflowPrefix)) {
            return trsId.substring(workflowPrefix.length());
        }
        return trsId;
    }

    private void createCategories(CategorizerConfig categorizerConfig, CreateCategoriesCommand createCategoriesCommand) {
        final Ontology ontology = readOntologies(createCategoriesCommand.getOntologyJsonPaths());
        final List<Ontology.Node> recommendedNodes = ontology.getNodes().stream().filter(Ontology.Node::recommendedForAnnotation).toList();
        LOG.info("Found {} recommended nodes", recommendedNodes.size());

        final ApiClient apiClient = setupApiClient(categorizerConfig.dockstoreServerUrl(), categorizerConfig.dockstoreToken());
        final OrganizationsApi organizationsApi = new OrganizationsApi(apiClient);

        final Organization organization = getAiOrganization(organizationsApi);

        for (Ontology.Node node: recommendedNodes) {
            final int maxCategoryNameLength = 90;
            final int maxCategoryDisplayNameLength = 90;
            final int maxCategoryTopicLength = 255;
            final Collection collection = new Collection();
            // TODO: adjust the code that truncates these fields to work better.
            // We might simply skip categories that have an ID or display name that's more than the limit,
            // and we might truncate the definition at the end of a sentence, if possible.
            collection.setName(StringUtils.truncate(node.id(), maxCategoryNameLength));
            collection.setDisplayName(StringUtils.truncate(node.label(), maxCategoryDisplayNameLength));
            collection.setTopic(StringUtils.truncate(node.definition(), maxCategoryTopicLength));
            collection.putMetadataItem("source", node.source());
            try {
                organizationsApi.createCollection(collection, organization.getId());
                LOG.info("Created category for node {}", node.id());
            } catch (ApiException e) {
                LOG.error("Unable to create category for node {}, skipping", node.id(), e);
            }
        }
    }

    private Organization getAiOrganization(OrganizationsApi organizationsApi) {
        try {
            return organizationsApi.getOrganizationByName(AI_ORGANIZATION_NAME);
        } catch (ApiException e) {
            exceptionMessage(e, "Unable to retrieve organization '%s'".formatted(AI_ORGANIZATION_NAME), API_ERROR);
            return null;
        }
    }

    private void listCategories(CategorizerConfig categorizerConfig, ListCategoriesCommand listCategoriesCommand) {
        final ApiClient apiClient = setupApiClient(categorizerConfig.dockstoreServerUrl(), categorizerConfig.dockstoreToken());
        final OrganizationsApi organizationsApi = new OrganizationsApi(apiClient);
        final Organization organization = getAiOrganization(organizationsApi);
        List<Collection> collections;
        try {
            collections = organizationsApi.getCollectionsFromOrganization(organization.getId(), "");
        } catch (ApiException e) {
            exceptionMessage(e, "Unable to retrieve collections for organization '%s'".formatted(AI_ORGANIZATION_NAME), API_ERROR);
            return;
        }
        LOG.info("Retrieved {} collections", collections.size());
        final List<String> ontologyPaths = listCategoriesCommand.getOntologyJsonPaths();
        if (ontologyPaths != null) {
            final Set<String> ontologyIds = readOntologies(ontologyPaths).getNodes().stream().map(Ontology.Node::id).collect(Collectors.toSet());
            collections = collections.stream().filter(c -> ontologyIds.contains(c.getName())).toList();
            LOG.info("Filtered to {} collections present in ontologies", collections.size());
        }
        List<CategoryId> categoryIds = collections.stream().map(c -> new CategoryId(c.getName())).toList();
        writeCsvToStdout(categoryIds, CategoryId.class);
    }

    private void deleteCategories(CategorizerConfig categorizerConfig, DeleteCategoriesCommand deleteCategoriesCommand) {
        final String path = deleteCategoriesCommand.getCategoriesCsvPath();
        final List<String> categoryIds = readCsv(path, CategoryId.class).stream().map(CategoryId::categoryId).toList();
        LOG.info("Read {} category IDs from {}", categoryIds.size(), path);
        final ApiClient apiClient = setupApiClient(categorizerConfig.dockstoreServerUrl(), categorizerConfig.dockstoreToken());
        final OrganizationsApi organizationsApi = new OrganizationsApi(apiClient);
        final Organization organization = getAiOrganization(organizationsApi);
        for (String categoryId: categoryIds) {
            try {
                final Collection collection = organizationsApi.getCollectionByName(AI_ORGANIZATION_NAME, categoryId);
                organizationsApi.deleteCollection(organization.getId(), collection.getId(), false);
                LOG.info("Deleted category '{}'", categoryId);
            } catch (ApiException e) {
                LOG.error("Unable to delete category '{}', skipping", categoryId, e);
            }
        }

        // TODO: after the categories are deleted, we need to do a bulk ES reindex
    }

    @JsonPropertyOrder({"trsId", "version"})
    public record TrsIdAndVersion(String trsId, String version) {
    }

    @JsonPropertyOrder({"trsId", "version", "categoryId", "isMember"})
    public record Categorization(String trsId, String version, String categoryId, boolean isMember) {
    }

    @JsonPropertyOrder({"categoryId"})
    public record CategoryId(String categoryId) {
    }
}

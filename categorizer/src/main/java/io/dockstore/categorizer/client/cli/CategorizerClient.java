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
import io.dockstore.categorizer.client.cli.CategorizerCommandLineArgs.ReindexEntriesCommand;
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
import io.dockstore.openapi.client.model.Workflow;
import io.dockstore.openapi.client.model.WorkflowSubClass;
import io.dockstore.utils.CsvReader;
import io.dockstore.utils.CsvWriter;
import io.dockstore.utils.EntryUtils;
import io.dockstore.utils.IOUtils;
import io.dockstore.utils.RetrievalUtils;
import io.dockstore.utils.ai.AIModel;
import io.dockstore.utils.ai.AIModelFactory;
import io.dockstore.utils.ai.AIModelType;
import io.dockstore.utils.ai.LimitExceededException;
import io.dockstore.utils.ai.LoggingAIModel;
import io.dockstore.utils.ai.TotalCostAIModel;
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
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CLI entry point for the Dockstore categorizer tool. Parses command-line arguments via JCommander
 * and dispatches to one of several commands: listing all or stale entries, AI-driven categorization
 * of entries against ontology nodes, creating/populating/listing/deleting Dockstore categories
 * in the Dockstore AI categorizer organization that represent the nodes of the backing ontologies.
 */
public class CategorizerClient {
    private static final Logger LOG = LoggerFactory.getLogger(CategorizerClient.class);
    private static final String AI_ORGANIZATION_NAME = "dockstoreai";
    private static final String WORKFLOW_TRS_PREFIX = "#workflow/";
    private static final String SERVICE_TRS_PREFIX = "#service/";
    private static final String NOTEBOOK_TRS_PREFIX = "#notebook/";

    CategorizerClient() {
    }

    /**
     * Parses command-line arguments and dispatches to the appropriate command handler.
     * Logs total wall-clock time on completion.
     */
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
        final ReindexEntriesCommand reindexEntriesCommand = new ReindexEntriesCommand();
        jCommander.addCommand(listAllEntriesCommand);
        jCommander.addCommand(listStaleEntriesCommand);
        jCommander.addCommand(categorizeEntriesCommand);
        jCommander.addCommand(populateCategoriesCommand);
        jCommander.addCommand(createCategoriesCommand);
        jCommander.addCommand(listCategoriesCommand);
        jCommander.addCommand(deleteCategoriesCommand);
        jCommander.addCommand(reindexEntriesCommand);

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
            case "reindex-entries" -> categorizerClient.reindexEntries(categorizerConfig, reindexEntriesCommand);
            default -> errorMessage("Unknown command", GENERIC_ERROR);
            }
        }

        if (jCommander.getParsedCommand() != null) {
            final Instant endTime = Instant.now();
            LOG.info("{} took {}", jCommander.getParsedCommand(), Duration.between(startTime, endTime));
        }
    }

    /** Fetches all published entries from Dockstore and writes them as CSV to stdout. */
    private void listAllEntries(CategorizerConfig categorizerConfig, ListAllEntriesCommand listAllEntriesCommand) {
        final ApiClient apiClient = setupApiClient(categorizerConfig.dockstoreServerUrl(), categorizerConfig.dockstoreToken());
        final ExtendedGa4GhApi extendedGa4GhApi = new ExtendedGa4GhApi(apiClient);
        final int max = listAllEntriesCommand.getMax();
        List<TrsIdAndVersion> allEntries = getAllEntriesFromDockstore(extendedGa4GhApi, max);
        writeCsvToStdout(allEntries, TrsIdAndVersion.class);
    }

    /** Pages through all published entries via the GA4GH API, up to {@code maxEntries}. */
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

    /** Fetches entries whose categorization is older than the configured interval and writes them as CSV to stdout. */
    private void listStaleEntries(CategorizerConfig categorizerConfig, ListStaleEntriesCommand listStaleEntriesCommand) {
        final ApiClient apiClient = setupApiClient(categorizerConfig.dockstoreServerUrl(), categorizerConfig.dockstoreToken());
        final EntriesApi entriesApi = new EntriesApi(apiClient);
        final int max = listStaleEntriesCommand.getMax();
        final long intervalSeconds = listStaleEntriesCommand.getIntervalSeconds();
        final List<TrsIdAndVersion> staleEntries = getStaleEntriesFromDockstore(entriesApi, intervalSeconds, max);
        writeCsvToStdout(staleEntries, TrsIdAndVersion.class);
    }

    /** Pages through entries not categorized within {@code intervalSeconds}, up to {@code maxEntries}. */
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

    /** Serializes {@code items} as CSV rows to stdout, using the field order declared on {@code pojoClass}. */
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
     * Creates the AI model to use for categorization, optionally wrapping it to log prompts and responses,
     * and wrapping the result to track total cost and token usage.
     * @param aiModelType the AI model to create
     * @param logPrompts whether to log prompts and responses
     */
    private TotalCostAIModel createAiModel(AIModelType aiModelType, boolean logPrompts) {
        AIModel aiModel = AIModelFactory.createModel(aiModelType);
        if (logPrompts) {
            aiModel = new LoggingAIModel(aiModel);
        }
        return new TotalCostAIModel(aiModel);
    }

    /**
     * AI-categorizes the entries listed in the input CSV and writes matching ontology node assignments to stdout as CSV.
     * Each entry is processed in a worker thread; per-entry failures are counted and logged but do not abort the run.
     * @param categorizerConfig server URL and API token
     * @param categorizeEntriesCommand parsed CLI args: ontology paths, input CSV, AI model, cost limit, thread count
     */
    private void categorizeEntries(CategorizerConfig categorizerConfig, CategorizeEntriesCommand categorizeEntriesCommand) {
        final String dockstoreServerUrl = categorizerConfig.dockstoreServerUrl();
        final String dockstoreToken = categorizerConfig.dockstoreToken();

        final List<String> ontologyPaths = categorizeEntriesCommand.getOntologyJsonPaths();
        final Ontology ontology = readOntologies(ontologyPaths);

        final String entriesPath = categorizeEntriesCommand.getEntriesCsvFilePath();
        List<TrsIdAndVersion> entries = readCsv(entriesPath, TrsIdAndVersion.class);
        LOG.info("Read {} entries from input file {}", entries.size(), entriesPath);

        AIModelType aiModelType = categorizeEntriesCommand.getAiModel();
        TotalCostAIModel aiModel = createAiModel(aiModelType, categorizeEntriesCommand.isLogPrompts());
        LOG.info("Categorizing entries using AI model {}", aiModelType.getModelId());

        double costLimit = categorizeEntriesCommand.getCostLimit();
        if (Double.isFinite(costLimit)) {
            LOG.info("Cost limit: ${}", costLimit);
        }

        final List<OntologyHandler> ontologyHandlers = List.of(
            new OperationOntologyHandler(),
            new TopicOntologyHandler(),
            new InputFormatOntologyHandler(),
            new InputDataOntologyHandler(),
            new OutputFormatOntologyHandler(),
            new OutputDataOntologyHandler()
        );
        checkOverlappingHandlers(ontologyHandlers, ontology);

        primeCache(ontologyHandlers, ontology, aiModel);

        final int threadCount = categorizeEntriesCommand.getThreadCount();
        final AtomicInteger numberOfFailures = new AtomicInteger(0);
        try (Writer writer = stdoutWriter(); CsvWriter<Categorization> categorizationsWriter = new CsvWriter<>(writer, Categorization.class)) {
            List<Runnable> runnables = entries.stream().<Runnable>map(entry -> () -> {
                final String trsId = entry.trsId();
                final String version = entry.version();
                try {
                    LOG.info("Categorizing entry {} version {}", trsId, version);
                    // Check if we've exceeded the cost limit.
                    // We check the limit at this point in the code to avoid needlessly retrieving the entry data.
                    if (aiModel.getTotalCost() > costLimit) {
                        throw new LimitExceededException(
                            String.format("Cost limit of $%.6f exceeded: total cost is $%.6f", costLimit, aiModel.getTotalCost()));
                    }
                    // Retrieve data about the entry.
                    final ApiClient apiClient = setupApiClient(dockstoreServerUrl, dockstoreToken);
                    final int maxFieldLength = 200_000;
                    final EntryData entryData = retrieveEntryData(apiClient, trsId, version).limit(maxFieldLength);
                    // For each ontology handler, categorize the entry into the appropriate nodes (categories).
                    boolean matchedAnyCategory = false;
                    for (OntologyHandler handler: ontologyHandlers) {
                        // Determine the "recommended for annotation" nodes that the handler covers.
                        List<Ontology.Node> candidateNodes = determineCandidateNodes(handler, ontology);
                        // Determine which nodes (categories) match the entry.
                        List<Ontology.Node> matchingNodes = handler.categorize(candidateNodes, entryData, aiModel);
                        // Write the entry and matching node information to the CSV.
                        synchronized (categorizationsWriter) {
                            for (Ontology.Node matchingNode: matchingNodes) {
                                categorizationsWriter.write(new Categorization(entry.trsId(), entry.version(), matchingNode.id(), true));
                                matchedAnyCategory = true;
                            }
                        }
                    }
                    // If the entry didn't match any category, indicate as much with a sentinel row.
                    if (!matchedAnyCategory) {
                        synchronized (categorizationsWriter) {
                            categorizationsWriter.write(new Categorization(entry.trsId(), entry.version(), "-", true));
                        }
                    }
                    LOG.info("Total cost so far: ${}", aiModel.getTotalCost());
                } catch (Exception ex) {
                    LOG.error("Unable to categorize entry with TRS ID {} and version {}, skipping", trsId, version, ex);
                    numberOfFailures.incrementAndGet();
                }
            }).toList();

            LOG.info("Categorizing {} entries using {} threads", entries.size(), threadCount);
            runAndWaitUntilDone(runnables, threadCount);
            LOG.info("Failed to categorize {} entries", numberOfFailures.get());
        } catch (IOException e) {
            exceptionMessage(e, "Unable to create new CSV output file", IO_ERROR);
        }
        LOG.info("Total cost: ${}", aiModel.getTotalCost());
    }

    /**
     * Determines the nodes (categories) that a given handler should consider when categorizing an entry.
     * A node is a candidate if it falls within the handler's coverage of the ontology and is marked as
     * recommended for annotation.
     * @param handler the ontology handler whose coverage determines which nodes are considered
     * @param ontology the full ontology to filter nodes from
     * @return the list of candidate nodes for the handler to categorize against
     */
    private List<Ontology.Node> determineCandidateNodes(OntologyHandler handler, Ontology ontology) {
        return handler.coverage(ontology).stream().filter(Ontology.Node::recommendedForAnnotation).toList();
    }

    /**
     * Runs a categorization on dummy data, sequentially and before the real, parallel categorization work begins,
     * so that the AI model's prompt cache (for content such as the per-handler ontology node lists, which is
     * identical across all entries) is already warm once the worker threads start. Without this, several worker
     * threads could race to populate the same cache entry on their first request, each paying the "cache miss" cost.
     * Failures are logged but do not abort the run.
     */
    private void primeCache(List<OntologyHandler> ontologyHandlers, Ontology ontology, AIModel aiModel) {
        LOG.info("Priming the AI model prompt cache");
        final EntryData dummyEntryData = new EntryData("workflow", "dummy/dummy-entry", "A dummy entry used to prime the AI model's prompt cache.",
            "This is placeholder descriptor file content used to prime the AI model's prompt cache.");
        for (OntologyHandler handler: ontologyHandlers) {
            List<Ontology.Node> candidateNodes = determineCandidateNodes(handler, ontology);
            try {
                handler.categorize(candidateNodes, dummyEntryData, aiModel);
            } catch (Exception ex) {
                LOG.error("Unable to prime the AI model prompt cache for handler {}", handler.getName(), ex);
            }
        }
    }

    /** Submits all runnables to a fixed thread pool and blocks until every task has finished. */
    private void runAndWaitUntilDone(List<Runnable> runnables, int threadCount) {
        try (ExecutorService es = Executors.newFixedThreadPool(threadCount)) {
            runnables.forEach(es::execute);
        }
    }

    /** Reads one or more ontology JSON files and merges them into a single {@link Ontology}. */
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

    /**
     * Aborts if any two handlers claim the same recommended-for-annotation ontology node,
     * which would produce duplicate entries in the categorization output.
     */
    private void checkOverlappingHandlers(List<OntologyHandler> handlers, Ontology ontology) {
        // For each ontology handler, calculate the IDs of the recommended-for-annotation nodes that it covers.
        // Concatenate the IDs into a single list.
        List<String> ids = handlers.stream().flatMap(h -> h.coverage(ontology).stream().filter(Ontology.Node::recommendedForAnnotation).map(Ontology.Node::id)).toList();
        // If there are duplicate IDs, multiple Ontology handlers cover the same recommended-for-annotation node.
        if (ids.size() != new HashSet<>(ids).size()) {
            errorMessage("Multiple OntologyHandlers cover the same recommended-for-annotation node.", GENERIC_ERROR);
        }
    }

    /** Reads all rows from a CSV file into a list of {@code pojoClass} instances. */
    private <T> List<T> readCsv(String path, Class<T> pojoClass) {
        try (Reader reader = IOUtils.reader(path); CsvReader<T> csvReader = new CsvReader<>(reader, pojoClass)) {
            return csvReader.readAll();
        } catch (IOException e) {
            exceptionMessage(e, "Unable to read CSV file: " + path, IO_ERROR);
            return List.of();
        }
    }

    /**
     * Fetches the entry's type, description, and primary descriptor content from Dockstore.
     * Throws if the requested version cannot be retrieved.
     */
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

    /**
     * Applies AI-generated categorizations to Dockstore: adds entries to the corresponding
     * category Collections and stamps each successfully categorized entry with the current time
     * as its "last categorized" date. Entries or categories that cannot be resolved are skipped.
     */
    private void populateCategories(CategorizerConfig categorizerConfig, PopulateCategoriesCommand populateCategoriesCommand) {
        String path = populateCategoriesCommand.getCategorizationsCsvPath();
        final List<Categorization> categorizations = readCsv(path, Categorization.class);

        final ApiClient apiClient = setupApiClient(categorizerConfig.dockstoreServerUrl(), categorizerConfig.dockstoreToken());
        final OrganizationsApi organizationsApi = new OrganizationsApi(apiClient);
        final WorkflowsApi workflowsApi = new WorkflowsApi(apiClient);

        final Organization organization = getAiOrganization(organizationsApi);

        // Retrieve AI-curated categories and map category IDs to the corresponding Dockstore Collections.
        // We'll use the resulting Map later to avoid some redundant requests.
        LOG.info("Retrieving AI-curated categories");
        final List<Collection> collections = getCollectionsFromOrganization(organizationsApi, organization);
        final Map<String, Collection> categoryIdToCollection = collections.stream()
            .collect(Collectors.toMap(Collection::getName, Function.identity()));

        // Map TRS Ids to the corresponding Dockstore Entries.
        // We'll use the resulting Map later to avoid some redundant requests.
        LOG.info("Mapping TRS IDs to Entries");
        final List<String> trsIds = categorizations.stream().map(Categorization::trsId).distinct().toList();
        final Map<String, Entry> trsIdToEntry = new HashMap<>();
        for (String trsId: trsIds) {
            try {
                trsIdToEntry.put(trsId, getEntryByTrsID(workflowsApi, trsId));
                LOG.info("Retrieved entry '{}'", trsId);
            } catch (ApiException e) {
                LOG.error("ApiException while retrieving entry '{}'", trsId, e);
            } catch (RuntimeException e) {
                LOG.error("Unexpected exception while retrieving entry '{}'", trsId, e);
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
                organizationsApi.addEntryToCollection(organization.getId(), collection.getId(), entry.getId(), null, "AI", false);
                LOG.info("Added entry {} to category {}", trsId, categoryId);
            } catch (Exception e) {
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
                entriesApi.setLastCategorizedDate(entry.getId(), "", null);
                LOG.info("Updated time of last categorization for entry {}", trsId);
            } catch (ApiException e) {
                LOG.error("Unable to update time of last categorization for entry {}", trsId, e);
            }
        }
    }

    /**
     * Retrieves a published Dockstore entry by its TRS ID. Supports every entry type: workflows, services,
     * notebooks, apptools, and tools. Workflows, services, notebooks, and apptools are looked up via
     * {@link WorkflowsApi#getPublishedWorkflowByPath}, which requires (and thus lets us pin down) the specific
     * subclass of the entry, since these entries can share a path with other subclasses defined in the same
     * source repository. Tools don't have this ambiguity, so they're looked up via the generic
     * {@link WorkflowsApi#getPublishedEntryByPath}. In all cases, the retrieved entry's TRS ID is confirmed to
     * match {@code trsId} before it's returned.
     */
    private Entry getEntryByTrsID(WorkflowsApi workflowsApi, String trsId) throws ApiException {
        final Entry entry;
        if (trsId.startsWith(WORKFLOW_TRS_PREFIX)) {
            entry = workflowToEntry(workflowsApi.getPublishedWorkflowByPath(
                trsId.substring(WORKFLOW_TRS_PREFIX.length()), WorkflowSubClass.BIOWORKFLOW, null, null));
        } else if (trsId.startsWith(SERVICE_TRS_PREFIX)) {
            entry = workflowToEntry(workflowsApi.getPublishedWorkflowByPath(
                trsId.substring(SERVICE_TRS_PREFIX.length()), WorkflowSubClass.SERVICE, null, null));
        } else if (trsId.startsWith(NOTEBOOK_TRS_PREFIX)) {
            entry = workflowToEntry(workflowsApi.getPublishedWorkflowByPath(
                trsId.substring(NOTEBOOK_TRS_PREFIX.length()), WorkflowSubClass.NOTEBOOK, null, null));
        } else {
            // No TRS prefix: the entry is either a Tool or an AppTool, which share the same (empty) TRS prefix
            // and so can't be distinguished from the TRS ID alone. Try AppTool first, since (unlike a Tool's
            // path) an AppTool's path can collide with a workflow/service/notebook defined in the same source
            // repository; fall back to the generic, unambiguous Tool lookup if it isn't an AppTool.
            Entry appToolOrTool;
            try {
                appToolOrTool = workflowToEntry(workflowsApi.getPublishedWorkflowByPath(trsId, WorkflowSubClass.APPTOOL, null, null));
            } catch (ApiException e) {
                appToolOrTool = workflowsApi.getPublishedEntryByPath(trsId);
            }
            entry = appToolOrTool;
        }

        // Confirm that the retrieved entry's TRS ID matches the original TRS ID,
        // to prevent a programming error from triggering an update using information from the wrong entry.
        if (!trsId.equals(entry.getTrsId())) {
            throw new TrsIdMismatchException("Retrieved entry has TRS ID '%s', expected '%s'".formatted(entry.getTrsId(), trsId));
        }
        return entry;
    }

    /**
     * Converts a {@link Workflow} (or one of its subclasses: BioWorkflow, Service, Notebook, AppTool) to an
     * {@link Entry}. The two types don't share a common supertype in the generated API client, so only the
     * fields needed by callers of {@link #getEntryByTrsID}, namely the ID and TRS ID, are copied over.
     */
    private Entry workflowToEntry(Workflow workflow) {
        return new Entry().id(workflow.getId()).trsId(workflow.getTrsId());
    }

    /**
     * Creates a Dockstore Collection in the AI organization for each recommended-for-annotation
     * ontology node. Nodes whose name or display name exceed field-length limits are skipped.
     * Prompts for confirmation before making any changes.
     */
    private void createCategories(CategorizerConfig categorizerConfig, CreateCategoriesCommand createCategoriesCommand) {
        confirmStructuralCategoryChange(categorizerConfig);
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

            final String name = node.id();
            final String displayName = node.label();
            final String topic = node.definition();

            // Check the name and display name, and if they are too long, skip the category.
            if (name.length() > maxCategoryNameLength) {
                LOG.error("Category name {} is too long (maximum allowed length is {} characters), skipping.", name, maxCategoryNameLength);
                continue;
            }
            if (displayName.length() > maxCategoryDisplayNameLength) {
                LOG.error("Category display name {} is too long (maximum allowed length is {} characters), skipping.", displayName, maxCategoryDisplayNameLength);
                continue;
            }

            final Collection collection = new Collection();
            collection.setName(name);
            collection.setDisplayName(displayName.replace('/', '-')); // TODO: loosen requirement in webservice
            collection.setTopic(StringUtils.truncate(topic, maxCategoryTopicLength));
            collection.putMetadataItem("source", node.source());
            try {
                organizationsApi.createCollection(collection, organization.getId());
                LOG.info("Created category for node {}", node.id());
            } catch (ApiException e) {
                LOG.error("Unable to create category for node {}, skipping", node.id(), e);
            }
        }
    }

    /**
     * Prints a warning to stderr and reads a line from stdin; aborts unless the user types "yes".
     * Used to guard commands that alter the number or structure of categories on the server.
     */
    private void confirmStructuralCategoryChange(CategorizerConfig categorizerConfig) {
        System.err.println("WARNING: This command changes the number or structure of the Categories on %s.".formatted(categorizerConfig.dockstoreServerUrl()));
        System.err.print("Do you want to continue? yes/no [enter]: ");
        final Scanner scanner = new Scanner(System.in);
        String answer = scanner.nextLine().trim();
        if (!"yes".equals(answer)) {
            errorMessage("Aborting command.", GENERIC_ERROR);
        }
    }

    /** Retrieves the {@value #AI_ORGANIZATION_NAME} organization from Dockstore, aborting on failure. */
    private Organization getAiOrganization(OrganizationsApi organizationsApi) {
        try {
            return organizationsApi.getOrganizationByName(AI_ORGANIZATION_NAME);
        } catch (ApiException e) {
            exceptionMessage(e, "Unable to retrieve organization '%s'".formatted(AI_ORGANIZATION_NAME), API_ERROR);
            return null;
        }
    }

    /** Retrieves all of the Collections from the specified Organization, aborting on failure. */
    private List<Collection> getCollectionsFromOrganization(OrganizationsApi organizationsApi, Organization organization) {
        try {
            return organizationsApi.getCollectionsFromOrganization(organization.getId(), "");
        } catch (ApiException e) {
            exceptionMessage(e, "Unable to retrieve collections from organization '%s'".formatted(organization.getName()), API_ERROR);
            return null;
        }
    }

    /**
     * Lists category IDs in the AI organization as CSV to stdout, optionally filtered to those
     * present in the given ontologies.
     */
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

    /**
     * Deletes the categories listed in the input CSV from the AI organization.
     * Prompts for confirmation before making any changes.
     */
    private void deleteCategories(CategorizerConfig categorizerConfig, DeleteCategoriesCommand deleteCategoriesCommand) {
        confirmStructuralCategoryChange(categorizerConfig);
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
    }

    /** Triggers a full reindex of all entries in the Dockstore search index. */
    private void reindexEntries(CategorizerConfig categorizerConfig, ReindexEntriesCommand reindexEntriesCommand) {
        final ApiClient apiClient = setupApiClient(categorizerConfig.dockstoreServerUrl(), categorizerConfig.dockstoreToken());
        final ExtendedGa4GhApi extendedGa4GhApi = new ExtendedGa4GhApi(apiClient);
        LOG.info("Reindexing entries");
        try {
            final Integer count = extendedGa4GhApi.updateTheWorkflowsAndToolsIndices();
            LOG.info("Reindexed {} entries", count);
        } catch (ApiException e) {
            exceptionMessage(e, "Unable to reindex entries", API_ERROR);
        }
    }

    /**
     * Thrown by {@link #getEntryByTrsID} when the retrieved entry's TRS ID does not match the requested TRS ID.
     */
    private static class TrsIdMismatchException extends RuntimeException {
        TrsIdMismatchException(String message) {
            super(message);
        }
    }

    /** A Dockstore entry identified by its TRS ID and a specific version name. */
    @JsonPropertyOrder({"trsId", "version"})
    public record TrsIdAndVersion(String trsId, String version) {
    }

    /** The result of categorizing an entry: the ontology node it was assigned to and whether it is a member of that category. */
    @JsonPropertyOrder({"trsId", "version", "categoryId", "isMember"})
    public record Categorization(String trsId, String version, String categoryId, boolean isMember) {
    }

    /** A single ontology node ID used as a Dockstore category name. */
    @JsonPropertyOrder({"categoryId"})
    public record CategoryId(String categoryId) {
    }
}

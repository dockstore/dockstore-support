package io.dockstore.tooltester.helper;

/**
 * @author gluu
 * @since 18/09/17
 */
public final class S3CacheHelper {

    private S3CacheHelper() {
        // hidden constructor
    }

    @SuppressWarnings("checkstyle:Indentation")
    public static String mapRepositoryToCache(String repository) {
        return switch (repository) {
            case "github.com/briandoconnor/dockstore-workflow-md5sum" -> "ga4gh-dream/md5sum";
            case "github.com/Barski-lab/ga4gh_challenge" -> "ga4gh-dream/biowardrobe";
            case "github.com/NCI-GDC/gdc-dnaseq-cwl/GDC_DNASeq" -> "ga4gh-dream/gdc";
            case "github.com/bcbio/bcbio_validation_workflows" -> "ga4gh-dream/bcbio";
            case "github.com/ENCODE-DCC/pipeline-container/encode-mapping-wdl", "github.com/ENCODE-DCC/pipeline-container/encode-mapping-cwl" -> "ga4gh-dream/encode";
            case "github.com/KnowEnG/cwl-gene-prioritization" -> "ga4gh-dream/knoweng";
            default -> "";
        };
    }
}

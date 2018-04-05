package io.dockstore.tooltester.helper;

/**
 * @author gluu
 * @since 18/09/17
 */
public class S3CacheHelper {
    public static String mapRepositoryToCache(String repository) {
        switch(repository) {
        case "github.com/briandoconnor/dockstore-workflow-md5sum":
            return "ga4gh-dream/md5sum";
        case "github.com/dockstore/hello_world":
            return "ga4gh-dream/hello_world";
        case "github.com/Barski-lab/ga4gh_challenge":
            return "ga4gh-dream/biowardrobe";
        case "github.com/NCI-GDC/gdc-dnaseq-cwl/GDC_DNASeq":
            return "ga4gh-dream/gdc";
        case "github.com/bcbio/bcbio_validation_workflows":
            return "ga4gh-dream/bcbio";
        case "github.com/ENCODE-DCC/pipeline-container/encode-mapping-wdl":
        case "github.com/ENCODE-DCC/pipeline-container/encode-mapping-cwl":
            return "ga4gh-dream/encode";
        case "github.com/KnowEnG/cwl-gene-prioritization":
            return "ga4gh-dream/knoweng";
        default:
            return "";
        }
    }
}

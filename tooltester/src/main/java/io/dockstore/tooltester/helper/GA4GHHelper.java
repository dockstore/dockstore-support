package io.dockstore.tooltester.helper;

/**
 * @author gluu
 * @since 18/09/17
 */
public class GA4GHHelper {
    public static String mapRepositoryToCache(String repository) {
        switch(repository) {
        case "briandoconnor/dockstore-workflow-md5sum":
            return "ga4gh-dream/md5sum";
        case "dockstore/hello_world":
            return "ga4gh-dream/hello_world";
        case "Barski-lab/biowardrobe_chipseq_se":
            return "ga4gh-dream/biowardrobe";
        case "NCI-GDC/gdc-dnaseq-cwl/GDC_DNASeq":
            return "ga4gh-dream/gdc";
        case "bcbio/bcbio_validation_workflows":
            return "ga4gh-dream/bcbio";
        case "ENCODE-DCC/pipeline-container/encode-mapping-cwl":
            return "ga4gh-dream/encode";
        case "knowengplaceholder":
            return "ga4gh-dream/knoweng";
        default:
            return "";
        }
    }
}

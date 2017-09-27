package io.dockstore.tooltester.helper;

/**
 * @author gluu
 * @since 18/09/17
 */
public class GA4GHHelper {
    public static String mapRepositoryToCache(String repository) {
        switch(repository) {
        case "briandoconnor/dockstore-workflow-md5sum":
            return "md5sum";
        case "dockstore/hello_world":
            return "hello_world";
        case "Barski-lab/biowardrobe_chipseq_se":
            return "biowardrobe";
        case "NCI-GDC/gdc-dnaseq-cwl/GDC_DNASeq":
            return "gdc";
        case "bcbio/bcbio_validation_workflows":
            return "bcbio";
        case "ENCODE-DCC/pipeline-container/encode-mapping-cwl":
            return "encode";
        case "knowengplaceholder":
            return "knoweng";
        default:
            return "";
        }
    }
}

package io.dockstore.categorizer.client.cli;

import static io.dockstore.categorizer.client.cli.CategorizerClient.removeCategoryTagsFromResponse;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CategorizerTest {

    @Test
    void testRemoveCategoryTagsFromResponse() {
        final String categories = "[{\"uri\": \"http://edamontology.org/operation_0004\", \"label\": \"Operation\"}]";
        assertEquals(categories, removeCategoryTagsFromResponse("<categories>" + categories + "</categories>"));
        assertEquals(categories, removeCategoryTagsFromResponse(categories));
    }
}

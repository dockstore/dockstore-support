package io.dockstore.utils.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.dockstore.utils.ai.AIModel.CacheMarker;
import io.dockstore.utils.ai.AIModel.Prompt;
import io.dockstore.utils.ai.AIModel.Text;
import org.junit.jupiter.api.Test;

class AIModelPromptBuilderTest {

    @Test
    void defaultsAreApplied() {
        Prompt prompt = new Prompt.Builder().build();
        assertEquals(0.0, prompt.temperature());
        assertEquals(100, prompt.outputTokens());
        assertTrue(prompt.systemContent().isEmpty());
        assertTrue(prompt.userContent().isEmpty());
    }

    @Test
    void textGoesToUserContentByDefault() {
        Prompt prompt = new Prompt.Builder().text("hello").build();
        assertEquals(1, prompt.userContent().size());
        assertEquals(new Text("hello"), prompt.userContent().get(0));
        assertTrue(prompt.systemContent().isEmpty());
    }

    @Test
    void explicitUserTextGoesToUserContent() {
        Prompt prompt = new Prompt.Builder().user().text("hello").build();
        assertEquals(1, prompt.userContent().size());
        assertEquals(new Text("hello"), prompt.userContent().get(0));
        assertTrue(prompt.systemContent().isEmpty());
    }

    @Test
    void systemTextGoesToSystemContent() {
        Prompt prompt = new Prompt.Builder().system().text("sys").build();
        assertEquals(1, prompt.systemContent().size());
        assertEquals(new Text("sys"), prompt.systemContent().get(0));
        assertTrue(prompt.userContent().isEmpty());
    }

    @Test
    void modeSwitchingRoutesContentCorrectly() {
        Prompt prompt = new Prompt.Builder()
            .system().text("sys")
            .user().text("usr")
            .build();
        assertEquals(1, prompt.systemContent().size());
        assertEquals(new Text("sys"), prompt.systemContent().get(0));
        assertEquals(1, prompt.userContent().size());
        assertEquals(new Text("usr"), prompt.userContent().get(0));
    }

    @Test
    void multipleTextCallsAccumulateInOrder() {
        Prompt prompt = new Prompt.Builder()
            .text("first")
            .text("second")
            .build();
        assertEquals(2, prompt.userContent().size());
        assertEquals(new Text("first"), prompt.userContent().get(0));
        assertEquals(new Text("second"), prompt.userContent().get(1));
    }

    @Test
    void cacheAddsMarkerToUserContent() {
        Prompt prompt = new Prompt.Builder().text("a").cache().build();
        assertEquals(2, prompt.userContent().size());
        assertEquals(new Text("a"), prompt.userContent().get(0));
        assertTrue(prompt.userContent().get(1) instanceof CacheMarker);
    }

    @Test
    void cacheAddsMarkerToSystemContent() {
        Prompt prompt = new Prompt.Builder().system().text("s").cache().build();
        assertEquals(2, prompt.systemContent().size());
        assertEquals(new Text("s"), prompt.systemContent().get(0));
        assertTrue(prompt.systemContent().get(1) instanceof CacheMarker);
    }

    @Test
    void temperatureIsSet() {
        Prompt prompt = new Prompt.Builder().temperature(0.7).build();
        assertEquals(0.7, prompt.temperature());
    }

    @Test
    void outputTokensIsSet() {
        Prompt prompt = new Prompt.Builder().outputTokens(512).build();
        assertEquals(512, prompt.outputTokens());
    }

    @Test
    void builderMethodsReturnSameInstance() {
        Prompt.Builder builder = new Prompt.Builder();
        assertSame(builder, builder.user());
        assertSame(builder, builder.system());
        assertSame(builder, builder.text("x"));
        assertSame(builder, builder.cache());
        assertSame(builder, builder.temperature(0.5));
        assertSame(builder, builder.outputTokens(200));
    }

    @Test
    void mixedSystemAndUserContent() {
        Prompt prompt = new Prompt.Builder()
            .system().text("sys1").text("sys2").cache()
            .user().text("usr1").cache().text("usr2")
            .system().text("sys3").cache()
            .user().text("usr3")
            .build();
        assertEquals(5, prompt.systemContent().size());
        assertEquals(new Text("sys1"), prompt.systemContent().get(0));
        assertEquals(new Text("sys2"), prompt.systemContent().get(1));
        assertTrue(prompt.systemContent().get(2) instanceof CacheMarker);
        assertEquals(new Text("sys3"), prompt.systemContent().get(3));
        assertTrue(prompt.systemContent().get(4) instanceof CacheMarker);
        assertEquals(4, prompt.userContent().size());
        assertEquals(new Text("usr1"), prompt.userContent().get(0));
        assertTrue(prompt.userContent().get(1) instanceof CacheMarker);
        assertEquals(new Text("usr2"), prompt.userContent().get(2));
        assertEquals(new Text("usr3"), prompt.userContent().get(3));
    }
}

package za.co.neroland.nerosecurity.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

/** A throwing listener never stops the next one. */
class ServerHooksTest {

    @Test
    void listenersAreIsolated() {
        List<String> seen = new ArrayList<>();
        List<Consumer<String>> listeners = List.of(
                s -> seen.add("first:" + s),
                s -> {
                    throw new IllegalStateException("deliberate test failure, safe to ignore");
                },
                s -> seen.add("third:" + s));
        ServerHooks.fireForTest(listeners, "tick");
        ServerHooks.fireForTest(listeners, "tick");
        assertEquals(List.of("first:tick", "third:tick", "first:tick", "third:tick"), seen);
    }
}

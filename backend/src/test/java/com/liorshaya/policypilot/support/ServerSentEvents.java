package com.liorshaya.policypilot.support;

import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * An event stream as a test reads it (the {@code text/event-stream} format: blocks separated by a blank line, each
 * with an {@code event:} name and {@code data:} lines): the events in the order they were sent, each with its JSON.
 */
public record ServerSentEvents(List<Event> events) {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    public ServerSentEvents {
        events = List.copyOf(events);
    }

    /** One event: its name and its data, parsed. */
    public record Event(String name, JsonNode data) {}

    public static ServerSentEvents parse(String stream) {
        List<Event> events = new ArrayList<>();
        for (String block : stream.split("\n\n")) {
            String name = null;
            StringBuilder data = new StringBuilder();
            for (String line : block.split("\n")) {
                if (line.startsWith("event:")) {
                    name = line.substring("event:".length()).trim();
                } else if (line.startsWith("data:")) {
                    data.append(data.isEmpty() ? "" : "\n").append(line.substring("data:".length()));
                }
            }
            if (name != null) {
                events.add(new Event(name, JSON.readTree(data.toString())));
            }
        }
        return new ServerSentEvents(events);
    }

    /** The events' names, in order. */
    public List<String> names() {
        return events.stream().map(Event::name).toList();
    }

    /** The data of every event with this name, in order. */
    public List<JsonNode> all(String name) {
        return events.stream().filter(event -> event.name().equals(name)).map(Event::data).toList();
    }

    /** The data of the first event with this name; an assertion error naming the stream when there is none. */
    public JsonNode first(String name) {
        return events.stream().filter(event -> event.name().equals(name)).map(Event::data).findFirst()
                .orElseThrow(() -> new AssertionError("no " + name + " event in the stream: " + names()));
    }
}

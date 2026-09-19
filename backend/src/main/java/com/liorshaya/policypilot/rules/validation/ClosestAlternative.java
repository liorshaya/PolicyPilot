package com.liorshaya.policypilot.rules.validation;

import com.networknt.schema.Error;
import com.networknt.schema.path.NodePath;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Reduces the errors of a failed {@code oneOf} or {@code anyOf} to those of the alternative the document most
 * nearly matches, so a condition with one wrong value reports that value instead of one complaint per shape.
 *
 * <p>Groups are resolved innermost first. An alternative's distance is the number of its errors located at the
 * node itself or at the node's {@code kind} or {@code type} discriminator; errors deeper in the node do not count,
 * because they mean the alternative was the right shape. The single closest alternative keeps its errors; on a tie
 * only the group's own summary error is kept.
 */
final class ClosestAlternative {

    private static final Set<String> COMBINATORS = Set.of("oneOf", "anyOf");
    private static final List<String> DISCRIMINATORS = List.of("/kind", "/type");
    private static final int NONE = -1;

    private ClosestAlternative() {}

    static List<Error> select(List<Error> errors) {
        return select(errors, 0);
    }

    /** One combinator: its own summary error and the errors of its alternatives. */
    private static final class Group {
        private @Nullable Error summary;
        private final Map<String, List<Error>> branches = new LinkedHashMap<>();
    }

    private static List<Error> select(List<Error> errors, int from) {
        List<Error> out = new ArrayList<>();
        Map<List<String>, Group> groups = new LinkedHashMap<>();
        for (Error error : errors) {
            List<String> path = segments(error.getEvaluationPath());
            int combinator = firstCombinator(path, from);
            if (combinator == NONE) {
                out.add(error);
                continue;
            }
            Group group = groups.computeIfAbsent(path.subList(0, combinator + 1), key -> new Group());
            if (combinator == path.size() - 1) {
                group.summary = error;
            } else {
                group.branches.computeIfAbsent(path.get(combinator + 1), key -> new ArrayList<>()).add(error);
            }
        }
        groups.forEach((prefix, group) -> out.addAll(resolve(prefix.size(), group)));
        return out;
    }

    private static List<Error> resolve(int depth, Group group) {
        Error summary = Objects.requireNonNull(group.summary, "a failed combinator reports a summary error");
        String node = summary.getInstanceLocation().toString();
        List<List<Error>> closest = new ArrayList<>();
        long best = Long.MAX_VALUE;
        for (List<Error> branch : group.branches.values()) {
            List<Error> selected = select(branch, depth + 1);
            long distance = selected.stream().filter(error -> atNode(error, node)).count();
            if (distance < best) {
                best = distance;
                closest.clear();
            }
            if (distance == best) {
                closest.add(selected);
            }
        }
        return closest.size() == 1 ? closest.getFirst() : List.of(summary);
    }

    private static boolean atNode(Error error, String node) {
        String location = error.getInstanceLocation().toString();
        return location.equals(node) || DISCRIMINATORS.stream().anyMatch(d -> location.equals(node + d));
    }

    private static int firstCombinator(List<String> path, int from) {
        for (int i = from; i < path.size(); i++) {
            if (COMBINATORS.contains(path.get(i))) {
                return i;
            }
        }
        return NONE;
    }

    private static List<String> segments(NodePath path) {
        List<String> out = new ArrayList<>(path.getNameCount());
        for (int i = 0; i < path.getNameCount(); i++) {
            out.add(String.valueOf(path.getElement(i)));
        }
        return out;
    }
}

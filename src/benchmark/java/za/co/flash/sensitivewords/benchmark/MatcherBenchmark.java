package za.co.flash.sensitivewords.benchmark;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;
import za.co.flash.sensitivewords.matcher.SensitiveWordMatcher;

/** Algorithm measurements only; excludes HTTP, database, and refresh transactions. */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class MatcherBenchmark {
    @Param({"228", "2000"}) public int vocabularySize;
    @Param({"64", "1024", "10000"}) public int messageLength;
    @Param({"none", "sparse", "dense"}) public String density;
    private List<String> terms;
    private SensitiveWordMatcher matcher;
    private String message;

    @Setup
    public void setup() {
        terms = new ArrayList<>();
        for (int index = 0; index < vocabularySize; index++) terms.add("secret" + index);
        matcher = SensitiveWordMatcher.compile(terms);
        String unit = switch (density) {
            case "dense" -> "secret0 ";
            case "sparse" -> "ordinary message with one secret0 and lots of harmless words ";
            default -> "ordinary harmless message ";
        };
        message = unit.repeat(messageLength / unit.length() + 1).substring(0, messageLength);
    }

    @Benchmark
    public String sanitize() { return matcher.sanitize(message); }

    @Benchmark
    public SensitiveWordMatcher compile() { return SensitiveWordMatcher.compile(terms); }
}

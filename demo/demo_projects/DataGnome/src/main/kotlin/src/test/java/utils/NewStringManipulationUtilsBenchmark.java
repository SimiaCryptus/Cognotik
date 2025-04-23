// src/test/java/utils/NewStringManipulationUtilsBenchmark.java
package utils;

import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
public class NewStringManipulationUtilsBenchmark {

    @Benchmark
    public void testRegexReplacePerformance() {
        NewStringManipulationUtils.regexReplace("hello world", "[aeiou]", "x");
    }

    // Add more benchmarks for other methods
}
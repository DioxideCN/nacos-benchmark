package cn.dioxide;

import cn.dioxide.benchmark.ConfigStabilityBenchmark;
import cn.dioxide.benchmark.ConfigTimeConsumingBenchmark;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * @author Dioxide.CN
 * @date 2024/8/28
 * @since 1.0
 */
public class BenchmarkStarter {
    
    public static void main(String[] args) throws Exception {
        benchmark1();
    }
    
    private static void benchmark1() {
        final int clientCnt = 10;
        final int preClientCnt = 10;
        new ConfigTimeConsumingBenchmark(clientCnt, preClientCnt).benchmark();
    }
    
    private static void benchmark2() throws Exception {
        Options options = new OptionsBuilder()
                .include(ConfigStabilityBenchmark.class.getSimpleName())
                .build();
        new Runner(options).run();
    }

}

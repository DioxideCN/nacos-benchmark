package cn.dioxide;

import cn.dioxide.benchmark.concurrency.ConfigBenchmark;
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
        final int clientCnt = 1;
        final int preClientCnt = 1;
        new ConfigBenchmark(clientCnt, preClientCnt).benchmark();
    }
    
    private static void benchmark2() throws Exception {
        Options options = new OptionsBuilder()
                .include(cn.dioxide.benchmark.stability.ConfigBenchmark.class.getSimpleName())
                .build();
        new Runner(options).run();
    }

}

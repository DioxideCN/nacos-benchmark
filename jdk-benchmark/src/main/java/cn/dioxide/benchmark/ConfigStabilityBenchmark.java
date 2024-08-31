package cn.dioxide.benchmark;

import cn.dioxide.infra.ClientConfig;
import cn.dioxide.infra.ConfigHelper;
import cn.dioxide.infra.RpcClientBuilder;
import com.alibaba.nacos.common.remote.client.RpcClient;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Dioxide.CN
 * @date 2024/8/30
 * @since 1.0
 */
@State(Scope.Thread)
public class ConfigStabilityBenchmark {
    
    private static final int CLIENT_CNT = 2; // cnt
    
    private static final int ACTION_PER_SEC = 10; // s
    
    private static final int TOTAL_SEC = 60; // s
    
    private static final List<RpcClient> CLIENTS = RpcClientBuilder.createBatch(CLIENT_CNT);
    
    private final AtomicInteger successCount = new AtomicInteger();
    
    private final AtomicInteger failureCount = new AtomicInteger();
    
    private final AtomicInteger noResponseCount = new AtomicInteger();
    
    @Benchmark
    @Fork(1)
    @Warmup(iterations = 0)
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @Measurement(iterations = TOTAL_SEC)
    public void benchmark(Blackhole blackhole) {
        for (RpcClient client : CLIENTS) {
            for (int i = 0; i < ACTION_PER_SEC; i++) {
                try {
                    final String dataID = STR."config.\{client.getName()}.\{UUID.randomUUID()}";
                    boolean result = ConfigHelper.publishConfig(client, dataID);
                    if (result) {
                        successCount.incrementAndGet();
                    } else {
                        failureCount.incrementAndGet();
                    }
                    blackhole.consume(result);
                } catch (Exception ex) {
                    noResponseCount.incrementAndGet();
                }
            }
        }
    }
    
    @TearDown(Level.Trial)
    public void tearDown() {
        System.out.printf("总成功请求 = %d, 总失败请求 = %d, 总无响应请求 = %d\n",
                successCount.get(), failureCount.get(), noResponseCount.get());
    }
    
}

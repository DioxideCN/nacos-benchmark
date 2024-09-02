package cn.dioxide.benchmark.stability;

import cn.dioxide.infra.ConfigHelper;
import cn.dioxide.infra.RpcClientBuilder;
import cn.dioxide.infra.Statistic;
import com.alibaba.nacos.common.remote.client.RpcClient;
import com.alibaba.nacos.common.utils.ConcurrentHashSet;
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
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Dioxide.CN
 * @date 2024/8/30
 * @since 1.0
 */
@State(Scope.Thread)
public class ConfigBenchmark {
    
    private static final int CLIENT_CNT = 50; // cnt
    
    private static final int ACTION_PER_CLIENT = 100; // s
    
    private static final int TOTAL_SEC = 10; // s
    
    private static final List<RpcClient> CLIENTS = RpcClientBuilder.createBatch(CLIENT_CNT);
    
    private static final Statistic STATISTIC = new Statistic();
    
    private static final ConcurrentHashSet<String> deletingMap = new ConcurrentHashSet<>();
    
    @Benchmark
    @Fork(1)
    @Warmup(iterations = 0)
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @Measurement(iterations = TOTAL_SEC)
    public void benchmark(Blackhole blackhole) {
        final AtomicInteger successCount = new AtomicInteger();
        final AtomicInteger failureCount = new AtomicInteger();
        final AtomicInteger pendingCount = new AtomicInteger();
        
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            final CountDownLatch startLatch = new CountDownLatch(1);
            
            for (RpcClient client : CLIENTS) {
                for (int i = 0; i < ACTION_PER_CLIENT; i++) {
                    executor.submit(() -> {
                        try {
                            startLatch.await();
                            
                            final String dataID =
                                    STR."config.\{client.getName()}.\{UUID.randomUUID()}.\{System.currentTimeMillis()}";
                            ConfigBenchmark.deletingMap.add(dataID);
                            boolean result = ConfigHelper.publishConfig(client, dataID);
                            if (result) {
                                successCount.incrementAndGet();
                            } else {
                                failureCount.incrementAndGet();
                            }
                            
                            blackhole.consume(result);
                        } catch (Exception ex) {
                            pendingCount.incrementAndGet();
                        }
                    });
                }
            }
            
            startLatch.countDown();
            executor.shutdown();
            
            try {
                if (executor.awaitTermination(120, TimeUnit.SECONDS)) {
                    STATISTIC.put(successCount.get(), failureCount.get(), pendingCount.get());
                } else {
                    System.out.println("Request Timeout!");
                }
            } catch (InterruptedException e) {
                System.err.println(STR."Await termination interrupted: \{e.getMessage()}");
                Thread.currentThread().interrupt();
            }
        }
    }
    
    @TearDown(Level.Trial)
    public void tearDown() {
        System.out.println(STATISTIC);
        deletingConfigs();
    }
    
    private void deletingConfigs() {
        if (ConfigBenchmark.deletingMap.isEmpty()) {
            System.out.println("No configs need to be delete...");
        } else {
            System.out.println(STR."Deleting \{ConfigBenchmark.deletingMap.size()} configs...");
            ConfigHelper.batchDeleteConfig(ConfigBenchmark.deletingMap);
        }
    }
    
}

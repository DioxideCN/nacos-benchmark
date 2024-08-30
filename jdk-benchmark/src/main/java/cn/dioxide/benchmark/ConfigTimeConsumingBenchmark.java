package cn.dioxide.benchmark;

import cn.dioxide.infra.ClientConfig;
import cn.dioxide.infra.ConfigHelper;
import cn.dioxide.infra.RpcClientBuilder;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.config.remote.request.ConfigPublishRequest;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.client.config.NacosConfigService;
import com.alibaba.nacos.client.config.impl.ClientWorker;
import com.alibaba.nacos.client.config.impl.ConfigTransportClient;
import com.alibaba.nacos.client.security.SecurityProxy;
import com.alibaba.nacos.client.utils.ParamUtil;
import com.alibaba.nacos.common.remote.client.RpcClient;
import com.alibaba.nacos.common.utils.MD5Utils;
import com.alibaba.nacos.plugin.auth.api.RequestResource;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Dioxide.CN
 * @date 2024/8/28
 * @since 1.0
 */
public class ConfigTimeConsumingBenchmark {
    
    final List<RpcClient> clients;
    
    final int preClientCnt;
    
    final int total;
    
    final ConfigService CONFIG = ClientConfig.getConn();
    
    final List<String> deletingList = Collections.synchronizedList(new ArrayList<>());;
    
    private final AtomicInteger successCount = new AtomicInteger(0);
    
    private final AtomicInteger failureCount = new AtomicInteger(0);
    
    private final ConcurrentLinkedQueue<Statistic> statistics = new ConcurrentLinkedQueue<>();
    
    private volatile boolean running = true;
    
    public ConfigTimeConsumingBenchmark(final int clientCnt, final int preClientCnt) {
        this.preClientCnt = preClientCnt;
        this.total = clientCnt * preClientCnt;
        // build clients
        this.clients = RpcClientBuilder.createBatch(clientCnt);
    }

    public void benchmark() {
        final CountDownLatch latch = new CountDownLatch(1);
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() -> {
            if (running) {
                recordStatistic();
            }
        }, 0, 20, TimeUnit.MILLISECONDS);
        
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            System.out.println("[Benchmark] Publish Config");
            for (RpcClient client : clients) {
                executor.submit(() -> {
                    try {
                        latch.await();
                        for (int cnt = 0; cnt < this.preClientCnt; cnt++) {
                            try {
                                final String dataID = STR."config.\{client.getName()}.\{UUID.randomUUID().toString()}";
                                if (ConfigHelper.publishConfig(client, dataID)) {
                                    this.successCount.incrementAndGet();
                                    this.deletingList.add(dataID);
                                } else {
                                    this.failureCount.incrementAndGet();
                                }
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                    } catch (InterruptedException e) {
                        failureCount.incrementAndGet();
                        Thread.currentThread().interrupt();
                    }
                });
            }
            latch.countDown();
            executor.shutdown();
            
            try {
                if (!executor.awaitTermination(120, TimeUnit.SECONDS)) {
                    System.out.println(STR."not finished result {process=(\{getSuccessCount() + getFailureCount()}/\{total}), success=\{getSuccessCount()}, fail=\{getFailureCount()}}");
                } else {
                    System.out.println(
                            STR."benchmark result {total=\{total}, success=\{getSuccessCount()}, fail=\{getFailureCount()}}");
                }
            } catch (InterruptedException e) {
                System.err.println(STR."Await termination interrupted: \{e.getMessage()}");
                Thread.currentThread().interrupt();
            }
            
            running = false;
            scheduler.shutdown();
            recordStatistic();
            
            System.out.println("[Process] Removing Configs");
            int removedCnt = 0;
            for (String dataID : this.deletingList) {
                try {
                    boolean removed = this.CONFIG.removeConfig(dataID, ClientConfig.DEFAULT_GROUP);
                    if (removed) removedCnt++;
                } catch (NacosException e) {
                    throw new RuntimeException(e);
                }
            }
            System.out.println(STR."remove result \{removedCnt}/\{total}");
            
            System.out.println("[Result]");
            System.out.println(Arrays.toString(this.statistics.stream().toArray()));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void recordStatistic() {
        int success = getSuccessCount();
        int failure = getFailureCount();
        int total = success + failure;
        statistics.add(new Statistic(success, failure, total, System.currentTimeMillis()));
    }
    
    public Integer getSuccessCount() {
        return successCount.get();
    }
    
    public Integer getFailureCount() {
        return failureCount.get();
    }
    
    private record Statistic(int success, int failure, int total, long timestamp) {
        @Override
        public String toString() {
            return STR."""
                    { type: 'success', cnt: \{success}, time: \{timestamp} },
                    { type: 'failure', cnt: \{failure}, time: \{timestamp} },
                    { type: 'total', cnt: \{total}, time: \{timestamp} },
                    """;
        }
    }

}

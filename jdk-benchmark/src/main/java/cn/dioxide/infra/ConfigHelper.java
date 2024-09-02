package cn.dioxide.infra;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.config.remote.request.ConfigPublishRequest;
import com.alibaba.nacos.api.config.remote.request.ConfigRemoveRequest;
import com.alibaba.nacos.api.config.remote.response.ConfigRemoveResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.client.config.NacosConfigService;
import com.alibaba.nacos.client.config.filter.impl.ConfigFilterChainManager;
import com.alibaba.nacos.client.config.filter.impl.ConfigRequest;
import com.alibaba.nacos.client.config.impl.ClientWorker;
import com.alibaba.nacos.client.config.impl.ConfigTransportClient;
import com.alibaba.nacos.client.security.SecurityProxy;
import com.alibaba.nacos.client.utils.ParamUtil;
import com.alibaba.nacos.common.remote.client.RpcClient;
import com.alibaba.nacos.common.utils.ConcurrentHashSet;
import com.alibaba.nacos.common.utils.MD5Utils;
import com.alibaba.nacos.plugin.auth.api.RequestResource;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * @author Dioxide.CN
 * @date 2024/8/30
 * @since 1.0
 */
public class ConfigHelper {
    
    static String namespace;
    
    static String encode;
    
    static SecurityProxy securityProxy;
    
    static ClientWorker.ConfigRpcTransportClient agent;
    
    static ConfigFilterChainManager configFilterChain;
    
    static List<RpcClient> deletingClient;
    
    static {
        try {
            ConfigService config = ClientConfig.getConn();
            Field workerField = NacosConfigService.class.getDeclaredField("worker");
            workerField.setAccessible(true);
            ClientWorker worker = (ClientWorker) workerField.get(config);
            
            Field namespaceField = NacosConfigService.class.getDeclaredField("namespace");
            namespaceField.setAccessible(true);
            ConfigHelper.namespace = (String) namespaceField.get(config);
            
            Field configFilterChainManagerField = NacosConfigService.class.getDeclaredField("configFilterChainManager");
            configFilterChainManagerField.setAccessible(true);
            ConfigHelper.configFilterChain = (ConfigFilterChainManager) configFilterChainManagerField.get(config);
            
            ConfigHelper.agent = (ClientWorker.ConfigRpcTransportClient) worker.getAgent();
            
            Field securityProxyField = ConfigTransportClient.class.getDeclaredField("securityProxy");
            securityProxyField.setAccessible(true);
            ConfigHelper.securityProxy = (SecurityProxy) securityProxyField.get(ConfigHelper.agent);
            
            Field encodeField = ConfigTransportClient.class.getDeclaredField("encode");
            encodeField.setAccessible(true);
            ConfigHelper.encode = (String) encodeField.get(ConfigHelper.agent);
            
            ArrayList<RpcClient> deletingClient = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                deletingClient.add(RpcClientBuilder.createOne(STR."deleting_client_\{i}"));
            }
            ConfigHelper.deletingClient = deletingClient;
        } catch (Exception ex) {
            throw new Error(ex);
        }
    }
    
    public static boolean publishConfig(RpcClient client, final String dataID) throws Exception {
        return ConfigHelper.requestProxy(client, buildPublishRequest(dataID), 3000L).isSuccess();
    }
    
    public static void batchDeleteConfig(final ConcurrentHashSet<String> dataIDs) {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            final int numClients = deletingClient.size();
            final int step = dataIDs.size() / 5;
            final List<String> dataIDList = new ArrayList<>(dataIDs);
            for (int cnt = 0; cnt < numClients; cnt++) {
                int start = cnt * step;
                int end = (cnt == numClients - 1) ? dataIDList.size() : (cnt + 1) * step;
                final List<String> subList = dataIDList.subList(start, end);
                final RpcClient client = deletingClient.get(cnt);
                executor.submit(() -> {
                    try {
                        for (String dataID : subList) {
                            requestProxy(client, buildRemoveRequest(dataID), 3000L);
                        }
                    } catch (NacosException e) {
                        e.printStackTrace();
                    }
                });
            }
            executor.shutdown();
            try {
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                    if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                        System.err.println("Executor did not terminate in the allotted time.");
                    }
                }
            } catch (InterruptedException ex) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    private static ConfigRemoveRequest buildRemoveRequest(final String dataID) {
        return new ConfigRemoveRequest(dataID, ClientConfig.DEFAULT_GROUP, namespace, null);
    }
    
    private static ConfigPublishRequest buildPublishRequest(final String dataID) throws NacosException {
        final String content = STR."content.\{dataID}";
        ConfigRequest cr = new ConfigRequest();
        cr.setDataId(dataID);
        cr.setTenant(namespace);
        cr.setGroup(ClientConfig.DEFAULT_GROUP);
        cr.setContent(content);
        cr.setType(ConfigType.getDefaultType().getType());
        ConfigHelper.configFilterChain.doFilter(cr, null);
        String encryptedDataKey = cr.getEncryptedDataKey();
        
        ConfigPublishRequest request = new ConfigPublishRequest(dataID, ClientConfig.DEFAULT_GROUP, namespace, cr.getContent());
        request.setCasMd5(null);
        request.putAdditionalParam("tag", null);
        request.putAdditionalParam("appName", null);
        request.putAdditionalParam("betaIps", null);
        request.putAdditionalParam("type", ConfigType.getDefaultType().getType());
        request.putAdditionalParam("encryptedDataKey", encryptedDataKey == null ? "" : encryptedDataKey);
        return request;
    }
    
    public static Response requestProxy(RpcClient rpcClientInner, Request request, long timeoutMills)
            throws NacosException {
        try {
            request.putAllHeader(ConfigHelper.getSecurityHeaders(request));
            request.putAllHeader(ConfigHelper.getCommonHeader());
        } catch (Exception e) {
            throw new NacosException(NacosException.CLIENT_INVALID_PARAM, e);
        }
        return rpcClientInner.request(request, timeoutMills);
    }
    
    private static Map<String, String> getCommonHeader() {
        final Map<String, String> headers = new HashMap<>(16);
        final String ts = String.valueOf(System.currentTimeMillis());
        final String token = MD5Utils.md5Hex(ts + ParamUtil.getAppKey(), Constants.ENCODE);
        headers.put(Constants.CLIENT_APPNAME_HEADER, ParamUtil.getAppName());
        headers.put(Constants.CLIENT_REQUEST_TS_HEADER, ts);
        headers.put(Constants.CLIENT_REQUEST_TOKEN_HEADER, token);
        headers.put("exConfigInfo", "true");
        headers.put(Constants.CHARSET_KEY, encode);
        return headers;
    }
    
    private static Map<String, String> getSecurityHeaders(Request request) {
        final String tenant;
        final String group;
        final String dataId;
        if (request instanceof ConfigPublishRequest publishRequest) {
            tenant = publishRequest.getTenant();
            group = publishRequest.getGroup();
            dataId = publishRequest.getDataId();
        } else if (request instanceof ConfigRemoveRequest removeRequest) {
            tenant = removeRequest.getTenant();
            group = removeRequest.getGroup();
            dataId = removeRequest.getDataId();
        } else {
            throw new Error("Unknown request type!");
        }
        final RequestResource resource = RequestResource
                .configBuilder()
                .setNamespace(tenant)
                .setGroup(group)
                .setResource(dataId)
                .build();
        return ConfigHelper.securityProxy.getIdentityContext(resource);
    }
    
}

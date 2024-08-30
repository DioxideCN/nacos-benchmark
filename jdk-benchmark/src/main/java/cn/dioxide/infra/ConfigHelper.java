package cn.dioxide.infra;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.config.remote.request.ConfigPublishRequest;
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
import com.alibaba.nacos.common.utils.MD5Utils;
import com.alibaba.nacos.plugin.auth.api.RequestResource;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

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
        } catch (Exception ex) {
            throw new Error(ex);
        }
    }
    
    public static boolean publishConfig(RpcClient client, final String dataID) throws Exception {
        return ConfigHelper.requestProxy(client, buildPublishRequest(dataID), 3000L).isSuccess();
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
        final String tenant = ((ConfigPublishRequest) request).getTenant();
        final String group = ((ConfigPublishRequest) request).getGroup();
        final String dataId = ((ConfigPublishRequest) request).getDataId();
        final RequestResource resource = RequestResource
                .configBuilder()
                .setNamespace(tenant)
                .setGroup(group)
                .setResource(dataId)
                .build();
        return ConfigHelper.securityProxy.getIdentityContext(resource);
    }
    
}

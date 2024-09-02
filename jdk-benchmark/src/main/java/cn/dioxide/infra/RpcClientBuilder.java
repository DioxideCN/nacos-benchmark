package cn.dioxide.infra;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.RemoteConstants;
import com.alibaba.nacos.client.config.NacosConfigService;
import com.alibaba.nacos.client.config.impl.ClientWorker;
import com.alibaba.nacos.client.config.impl.ConfigTransportClient;
import com.alibaba.nacos.client.security.SecurityProxy;
import com.alibaba.nacos.client.utils.AppNameUtils;
import com.alibaba.nacos.client.utils.EnvUtil;
import com.alibaba.nacos.common.remote.ConnectionType;
import com.alibaba.nacos.common.remote.client.Connection;
import com.alibaba.nacos.common.remote.client.ConnectionEventListener;
import com.alibaba.nacos.common.remote.client.RpcClient;
import com.alibaba.nacos.common.remote.client.RpcClientFactory;
import com.alibaba.nacos.common.remote.client.RpcClientTlsConfig;
import com.alibaba.nacos.common.remote.client.RpcClientTlsConfigFactory;
import com.alibaba.nacos.common.remote.client.ServerListFactory;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @author Dioxide.CN
 * @date 2024/8/29
 * @since 1.0
 */
public class RpcClientBuilder {
    
    private static Method ensureRpcClientMethod;
    
    static {
        try {
            final Method ensureRpcClientMethod =
                    ClientWorker.ConfigRpcTransportClient.class.getDeclaredMethod("ensureRpcClient",
                    String.class);
            ensureRpcClientMethod.setAccessible(true);
            RpcClientBuilder.ensureRpcClientMethod = ensureRpcClientMethod;
        } catch (Exception ex) {
            throw new Error(ex);
        }
    }
    
    public static List<RpcClient> createBatch(final int cnt) {
        List<RpcClient> clients = new ArrayList<>(cnt);
        for (int i = 0; i < cnt; i++) {
            RpcClient client;
            try {
                client = RpcClientBuilder.createOne(String.valueOf(i));
            } catch (NacosException e) {
                throw new RuntimeException(e);
            }
            if (!client.isWaitInitiated()) {
                clients.add(client);
            }
        }
        return clients;
    }
    
    public static RpcClient createOne(final String taskId) throws NacosException {
        try {
            return (RpcClient) RpcClientBuilder.ensureRpcClientMethod.invoke(ConfigHelper.agent, taskId);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new Error(e);
        }
    }
    
}

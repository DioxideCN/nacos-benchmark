package cn.dioxide.infra;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;

import java.util.Properties;

/**
 * @author Dioxide.CN
 * @date 2024/8/28
 * @since 1.0
 */
public class ClientConfig {
    
    static Properties properties;
    
    static ConfigService config;
    
    static final String SERVER_ADDR = "localhost:8848";
    
    public static final String DEFAULT_GROUP = "DEFAULT_GROUP";
    
    public static ConfigService getConn() {
        if (config != null) return config;
        
        try {
            Properties properties = new Properties();
            properties.put(PropertyKeyConst.SERVER_ADDR, SERVER_ADDR);
            ClientConfig.properties = properties;
            config = NacosFactory.createConfigService(ClientConfig.properties);
            return config;
        } catch (NacosException e) {
            throw new RuntimeException(e);
        }
    }

}

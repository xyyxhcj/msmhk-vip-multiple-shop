package vip.msmhk.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import vip.msmhk.common.config.BaseYmlProperties;

/**
 * @author xyyxhcj@qq.com
 * @since 2020/11/07
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "system.properties")
public class ChatProperties extends BaseYmlProperties {
    private int nettyPort;
    private String websocketPath;
    private String rsaPrivateKey;
}

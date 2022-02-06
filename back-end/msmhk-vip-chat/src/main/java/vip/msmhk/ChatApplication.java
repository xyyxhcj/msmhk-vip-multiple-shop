package vip.msmhk;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import vip.msmhk.common.aspect.DictAspect;
import vip.msmhk.common.config.ElasticSearchConfig;
import vip.msmhk.common.config.RedisConfig;
import vip.msmhk.common.support.InitCommon;
import vip.msmhk.config.ChatProperties;

/**
 * @author xyyxhcj@qq.com
 * @since 2021/06/25
 */
@SpringBootApplication
@ComponentScan(excludeFilters = {
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = {"(?!vip.msmhk.common.service.chat.*)vip.msmhk.common.service.*"}),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {DictAspect.class, InitCommon.class, ElasticSearchConfig.class, RedisConfig.class})})
@EnableConfigurationProperties({ChatProperties.class})
public class ChatApplication {
    public static void main(String[] args) {
        new SpringApplicationBuilder(ChatApplication.class).web(WebApplicationType.NONE).run(args);
    }
}

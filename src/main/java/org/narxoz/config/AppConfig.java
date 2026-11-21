package org.narxoz.config;

import org.narxoz.client.DownstreamProperties;
import org.narxoz.domain.OnlyApprovedCanCancelRule;
import org.narxoz.domain.Rule;
import org.narxoz.domain.TransitionRule;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DownstreamProperties.class) // app.downstream.* from yaml
public class AppConfig {

    @Bean
    public Rule rules() {
        Rule table = new TransitionRule();
        Rule stopFactor = new OnlyApprovedCanCancelRule();
        return (id, from, to) -> {
            table.check(id, from, to);
            stopFactor.check(id, from, to);
        };
    }
}

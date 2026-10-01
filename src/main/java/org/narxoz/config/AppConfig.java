package org.narxoz.config;

import org.narxoz.domain.OnlyApprovedCanCancelRule;
import org.narxoz.domain.Rule;
import org.narxoz.domain.TransitionRule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Bean
    public Rule transitionRule() {
        return new TransitionRule();
    }

    @Bean
    public Rule onlyApprovedCanCancelRule() {
        return new OnlyApprovedCanCancelRule();
    }
}
package com.example.configserver;

import java.util.List;
import java.util.Map;

import org.springframework.cloud.bindings.Binding;
import org.springframework.cloud.bindings.Bindings;
import org.springframework.cloud.bindings.boot.BindingsPropertiesProcessor;
import org.springframework.core.env.Environment;

import lombok.extern.slf4j.Slf4j;

/**
 * 
 * @see <a href=
 *      "https://github.com/spring-cloud/spring-cloud-bindings#extending-spring-boot-configuration">Extending
 *      Spring Boot Configuration</a>
 * @see <a href="https://cloud.spring.io/spring-cloud-config/reference/html/#_git_backend">Spring Cloud Config Git Backend</a>
 */
@Slf4j
public class GitAuthBindingsPropertiesProcessor implements BindingsPropertiesProcessor {

    public static final String TYPE = "configserver-git-auth";

    @Override
    public void process(Environment environment, Bindings bindings, Map<String, Object> properties) {
        if (!environment.getProperty("spring.cloud.config.server.bindings.git-auth.enable", Boolean.class, true)) {
            return;
        }
        List<Binding> myBindings = bindings.filterBindings(TYPE);
        if (myBindings.size() == 0) {
            return;
        }
        // reference: https://cloud.spring.io/spring-cloud-config/reference/html/#_authentication

        // use MapMapper from spring cloud someday? 
        // https://github.com/spring-cloud/spring-cloud-bindings/blob/main/src/main/java/org/springframework/cloud/bindings/boot/ConfigServerBindingsPropertiesProcessor.java#L43
        Map<String, String> secret = myBindings.get(0).getSecret();
        properties.put("spring.cloud.config.server.git.uri", secret.get("uri"));
        properties.put("spring.cloud.config.server.git.username", secret.get("username"));
        properties.put("spring.cloud.config.server.git.password", secret.get("password"));
        properties.put("spring.cloud.config.server.git.skipSslValidation", secret.get("skipSslValidation"));
        
        log.info("Mapped spring.cloud.config.server.git.uri to {}", properties.get("spring.cloud.config.server.git.uri"));
    }

}

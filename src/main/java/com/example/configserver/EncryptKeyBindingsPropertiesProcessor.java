package com.example.configserver;

import java.util.List;
import java.util.Map;

import org.springframework.cloud.bindings.Binding;
import org.springframework.cloud.bindings.Bindings;
import org.springframework.cloud.bindings.boot.BindingsPropertiesProcessor;
import org.springframework.core.env.Environment;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EncryptKeyBindingsPropertiesProcessor implements BindingsPropertiesProcessor {

    public static final String TYPE = "configserver-encrypt-key";

    @Override
    public void process(Environment environment, Bindings bindings, Map<String, Object> properties) {
        if (!environment.getProperty("spring.cloud.config.server.bindings.encrypt-key.enable", Boolean.class, true)) {
            return;
        }
        List<Binding> myBindings = bindings.filterBindings(TYPE);
        if (myBindings.size() == 0) {
            return;
        }
        // reference: https://cloud.spring.io/spring-cloud-config/reference/html/#_key_management

        // use MapMapper from spring cloud someday? 
        // https://github.com/spring-cloud/spring-cloud-bindings/blob/main/src/main/java/org/springframework/cloud/bindings/boot/ConfigServerBindingsPropertiesProcessor.java#L43
        Map<String, String> secret = myBindings.get(0).getSecret();
        properties.put("encrypt.key", secret.get("key"));
        
        log.info("Mapped encrypt.key from binding");
    }
    
}

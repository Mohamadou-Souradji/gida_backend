package com.gidajari.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.hibernate.Hibernate;
import org.hibernate.proxy.HibernateProxy;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * Serialisation JSON des relations LAZY : un proxy Hibernate est remplace par
 * l'entite reelle avant ecriture. Indispensable avec l'heritage User -> 6 profils :
 * sans cela, le candidat d'une candidature ou le prestataire d'un sequestre
 * seraient serialises comme un simple "User" sans leurs champs de profil
 * (nom commercial, corps de metier...).
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer hibernateProxyModule() {
        SimpleModule module = new SimpleModule("hibernate-unproxy");
        module.addSerializer(HibernateProxy.class, new JsonSerializer<HibernateProxy>() {
            @Override
            public void serialize(HibernateProxy proxy, JsonGenerator gen, SerializerProvider provider) throws IOException {
                Object reel = Hibernate.unproxy(proxy);
                provider.findValueSerializer(reel.getClass()).serialize(reel, gen, provider);
            }
        });
        return builder -> builder.modulesToInstall(module);
    }
}

package com.gidajari.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Sert les fichiers uploades (voir FileStorageService) en lecture via HTTP. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${gida-jari.fichiers.dossier-stockage:./uploads}")
    private String dossierStockage;

    @Value("${gida-jari.fichiers.url-publique-base:/files}")
    private String urlPubliqueBase;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String pattern = urlPubliqueBase.endsWith("/") ? urlPubliqueBase + "**" : urlPubliqueBase + "/**";
        String location = dossierStockage.endsWith("/") ? "file:" + dossierStockage : "file:" + dossierStockage + "/";
        registry.addResourceHandler(pattern).addResourceLocations(location);
    }
}

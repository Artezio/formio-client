package com.artezio.bpm.services.integration.cdi;

import com.artezio.bpm.services.integration.FileStorage;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Default;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import java.util.logging.Logger;

@Named
@ApplicationScoped
public class FileStorageProducer {

    private final Logger log = Logger.getLogger(FileStorageProducer.class.getName());

    @Inject
    @DefaultImplementation
    private FileStorage defaultImplementation;
    @Inject
    @ConcreteImplementation
    private Provider<FileStorage> nonDefaultImplementationsProvider;
    private FileStorage concreteImplementation;

    @PostConstruct
    private void selectConcreteImplementation() {
        try {
            concreteImplementation = nonDefaultImplementationsProvider.get();
            log.info(String.format("Using File Storage implementation class %s", concreteImplementation.getClass().getName()));
        } catch (Exception ex) {
            log.info("Suppressing an exception, using default Base64UrlFileStorage. Details in the stack trace below");
            ex.printStackTrace();
            concreteImplementation = defaultImplementation;
        }
    }

    @Produces
    @Default
    public FileStorage getFileStorage() {
        return concreteImplementation;
    }
}

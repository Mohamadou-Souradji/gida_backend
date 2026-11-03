package com.gidajari.service.exception;

/** Le code n'a pas pu etre envoye (serveur mail ou SMS indisponible) : traduit en HTTP 503. */
public class EnvoiImpossibleException extends RuntimeException {
    public EnvoiImpossibleException(String message) {
        super(message);
    }
}

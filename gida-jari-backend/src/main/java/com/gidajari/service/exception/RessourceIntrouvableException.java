package com.gidajari.service.exception;

/** Ressource inexistante (traduite en HTTP 404). */
public class RessourceIntrouvableException extends RuntimeException {
    public RessourceIntrouvableException(String message) {
        super(message);
    }
}

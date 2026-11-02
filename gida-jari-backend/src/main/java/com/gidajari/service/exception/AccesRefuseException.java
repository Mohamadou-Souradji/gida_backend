package com.gidajari.service.exception;

/** Action interdite a l'utilisateur connecte (traduite en HTTP 403). */
public class AccesRefuseException extends RuntimeException {
    public AccesRefuseException(String message) {
        super(message);
    }
}

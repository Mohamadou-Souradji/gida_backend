package com.gidajari.exceptionhandling;

import com.gidajari.service.exception.AccesRefuseException;
import com.gidajari.service.exception.KycInsuffisantException;
import com.gidajari.service.exception.RessourceIntrouvableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Traduit les exceptions metier en reponses HTTP exploitables par le frontend :
 * toujours un corps JSON {erreur, message} pour que l'interface puisse l'afficher.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(KycInsuffisantException.class)
    public ResponseEntity<Map<String, Object>> gererKycInsuffisant(KycInsuffisantException ex) {
        Map<String, Object> corps = corps("KYC_INSUFFISANT", ex.getMessage());
        corps.put("niveauActuel", ex.getNiveauActuel().name());
        corps.put("niveauRequis", ex.getNiveauRequis().name());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(corps);
    }

    @ExceptionHandler({AccesRefuseException.class, AccessDeniedException.class})
    public ResponseEntity<Map<String, Object>> gererAccesRefuse(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(corps("ACCES_REFUSE", ex.getMessage()));
    }

    @ExceptionHandler(RessourceIntrouvableException.class)
    public ResponseEntity<Map<String, Object>> gererIntrouvable(RessourceIntrouvableException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(corps("INTROUVABLE", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> gererEtatIllegal(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(corps("ETAT_INVALIDE", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> gererArgumentIllegal(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(corps("ARGUMENT_INVALIDE", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> gererValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + " : " + e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest().body(corps("VALIDATION", detail));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, Object>> gererRequeteInvalide(Exception ex) {
        return ResponseEntity.badRequest().body(corps("REQUETE_INVALIDE", "Requête mal formée : " + ex.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> gererFichierTropGros(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(corps("FICHIER_TROP_GROS", "Fichier limité à 15 Mo"));
    }

    private Map<String, Object> corps(String erreur, String message) {
        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("erreur", erreur);
        corps.put("message", message);
        return corps;
    }
}

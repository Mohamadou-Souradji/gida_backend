package com.gidajari.service;

import com.gidajari.entity.OTPVerification;
import com.gidajari.entity.enums.OtpChannel;
import com.gidajari.integration.EmailGateway;
import com.gidajari.integration.SmsGateway;
import com.gidajari.integration.SmtpEmailGateway;
import com.gidajari.repository.OTPVerificationRepository;
import com.gidajari.service.exception.EnvoiImpossibleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Code de connexion envoye par email (V5). */
class CodeParEmailTest {

    private OTPVerificationRepository repo;
    private EmailGateway email;
    private SmsGateway sms;
    private OTPService service;
    private final BCryptPasswordEncoder encodeur = new BCryptPasswordEncoder(4);

    @BeforeEach
    void init() {
        repo = Mockito.mock(OTPVerificationRepository.class);
        email = Mockito.mock(EmailGateway.class);
        sms = Mockito.mock(SmsGateway.class);
        service = new OTPService(repo, encodeur, sms, email);
        ReflectionTestUtils.setField(service, "longueur", 6);
        ReflectionTestUtils.setField(service, "expirationMinutes", 5);
        ReflectionTestUtils.setField(service, "tentativesMax", 5);
        ReflectionTestUtils.setField(service, "demandesMax", 3);
        ReflectionTestUtils.setField(service, "canal", "email");
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void emailNormaliseEtValide() {
        assertThat(OTPService.normaliserEmail("  Abdoul.Hafiz@Gmail.COM ")).isEqualTo("abdoul.hafiz@gmail.com");
        assertThat(OTPService.normaliserEmail("a+test@sous.domaine.ne")).isEqualTo("a+test@sous.domaine.ne");
        for (String faux : new String[]{"", "abc", "a@b", "a b@c.com", "a@b.c", "x@y.com\r\nBcc: z@w.com"}) {
            assertThatThrownBy(() -> OTPService.normaliserEmail(faux)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void emailMasque() {
        assertThat(OTPService.masquerEmail("abdoul@gmail.com")).isEqualTo("a***l@gmail.com");
        assertThat(OTPService.masquerEmail("ab@gmail.com")).isEqualTo("a***@gmail.com");
    }

    @Test
    void leCodePartParEmailEtSeVerifie() {
        when(email.envoyer(anyString(), anyString(), anyString(), anyString())).thenReturn(true);
        service.genererEtEnvoyer("+22790000001", "moussa@gmail.com");

        ArgumentCaptor<String> sujet = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> texte = ArgumentCaptor.forClass(String.class);
        verify(email).envoyer(eq("moussa@gmail.com"), sujet.capture(), texte.capture(), anyString());
        verifyNoInteractions(sms);
        String code = sujet.getValue().substring(0, 6);
        assertThat(code).matches("\\d{6}");
        assertThat(texte.getValue()).contains(code).contains("5 minutes");

        ArgumentCaptor<OTPVerification> enregistre = ArgumentCaptor.forClass(OTPVerification.class);
        verify(repo).save(enregistre.capture());
        OTPVerification otp = enregistre.getValue();
        assertThat(otp.getCanal()).isEqualTo(OtpChannel.EMAIL);
        assertThat(otp.getEmail()).isEqualTo("moussa@gmail.com");
        assertThat(otp.getCodeHash()).doesNotContain(code);

        when(repo.findTopByPhoneAndUtiliseFalseOrderByCreatedAtDesc("+22790000001")).thenReturn(Optional.of(otp));
        assertThat(service.verifier("+22790000001", "000000x")).isEmpty();
        assertThat(service.verifier("+22790000001", code)).contains(otp);
        assertThat(otp.isUtilise()).isTrue();
    }

    @Test
    void echecDuServeurMailRemonteUneErreur() {
        when(email.envoyer(anyString(), anyString(), anyString(), anyString())).thenReturn(false);
        assertThatThrownBy(() -> service.genererEtEnvoyer("+22790000001", "moussa@gmail.com"))
                .isInstanceOf(EnvoiImpossibleException.class);
    }

    @Test
    void emailObligatoireEnModeEmailMaisPasEnModeSms() {
        assertThatThrownBy(() -> service.genererEtEnvoyer("+22790000001", null)).isInstanceOf(IllegalArgumentException.class);
        ReflectionTestUtils.setField(service, "canal", "sms");
        assertThat(service.parEmail()).isFalse();
        service.genererEtEnvoyer("+22790000001", null);
        verify(sms).envoyer(eq("+22790000001"), anyString());
        verifyNoInteractions(email);
    }

    @Test
    void messageMimeCorrect() {
        SmtpEmailGateway smtp = new SmtpEmailGateway("smtp.exemple.ne", 587, "starttls", "u", "p",
                "no-reply@gidajari.com", "Gida Jari");
        String m = smtp.construireMessage("moussa@gmail.com", "123456 est votre code Gida Jari", "Code : 123456 é", "<b>123456</b>");
        assertThat(m).contains("To: <moussa@gmail.com>\r\n").contains("MIME-Version: 1.0\r\n")
                .contains("Subject: =?UTF-8?B?" + Base64.getEncoder().encodeToString(
                        "123456 est votre code Gida Jari".getBytes(StandardCharsets.UTF_8)) + "?=")
                .contains("Content-Type: text/plain; charset=UTF-8").contains("Content-Type: text/html; charset=UTF-8");
        for (String ligne : m.split("\r\n")) {
            assertThat(ligne).doesNotStartWith(".");
        }
        assertThatThrownBy(() -> new SmtpEmailGateway("h", 25, "tls?", "", "", "a@b.co", "X"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

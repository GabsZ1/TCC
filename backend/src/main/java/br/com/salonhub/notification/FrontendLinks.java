package br.com.salonhub.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Monta os links do frontend que vão dentro dos e-mails. */
@Component
public class FrontendLinks {

    private final String frontendUrl;

    public FrontendLinks(@Value("${app.frontend-url}") String frontendUrl) {
        this.frontendUrl = frontendUrl.endsWith("/")
                ? frontendUrl.substring(0, frontendUrl.length() - 1)
                : frontendUrl;
    }

    public String firstAccess(String token) {
        return frontendUrl + "/primeiro-acesso?token=" + encode(token);
    }

    public String passwordReset(String token) {
        return frontendUrl + "/redefinir-senha?token=" + encode(token);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}

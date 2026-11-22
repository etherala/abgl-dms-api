package com.abgl.dms.i18n;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApplicationMessagesTest {

    @Test
    void resolvesEnglishByDefault() {
        assertEquals("Invalid username or password", messages("en")
                .get(MessageKeys.AUTH_INVALID_CREDENTIALS));
    }

    @Test
    void resolvesConfiguredSpanishBundle() {
        assertEquals("Nombre de usuario o contraseña no válidos", messages("es")
                .get(MessageKeys.AUTH_INVALID_CREDENTIALS));
    }

    @Test
    void fallsBackToEnglishForAnUnbundledLocale() {
        assertEquals("Request failed", messages("fr")
                .get(MessageKeys.REQUEST_FAILED));
    }

    private ApplicationMessages messages(String language) {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        return new ApplicationMessages(source, language);
    }
}

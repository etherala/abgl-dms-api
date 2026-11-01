package com.abgl.dms.i18n;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Objects;

@Component
public class ApplicationMessages {

    private final MessageSource messageSource;
    private final Locale locale;

    public ApplicationMessages(
            MessageSource messageSource,
            @Value("${app.language:en}") String languageTag) {
        this.messageSource = messageSource;
        this.locale = Locale.forLanguageTag(Objects.requireNonNull(languageTag).trim());
        if (locale.getLanguage().isBlank()) {
            throw new IllegalArgumentException("app.language must be a valid language tag");
        }
    }

    public String get(String key, Object... arguments) {
        return messageSource.getMessage(key, arguments, locale);
    }
}

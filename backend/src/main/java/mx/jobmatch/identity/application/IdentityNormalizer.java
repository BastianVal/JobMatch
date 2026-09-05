package mx.jobmatch.identity.application;

import java.text.Normalizer;
import java.util.Locale;

public final class IdentityNormalizer {
    private IdentityNormalizer() {}
    public static String email(String value) {
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }
}


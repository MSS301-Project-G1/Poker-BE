package com.msspoker.chatservice.validator;

import com.msspoker.chatservice.exception.ChatApiException;

import io.github.kshired.banword.BanWordValidator;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Applies the same server-side word policy to every message transport and conversation type. */
@Component
public class ChatProfanityValidator {
    private static final Pattern INVISIBLE = Pattern.compile("\\p{Cf}");
    private static final Pattern SEPARATORS = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern INSERTED_SYMBOLS =
            Pattern.compile("[^\\p{L}\\p{M}\\p{N}\\p{Z}\\s]+");
    private static final Pattern REPEATED = Pattern.compile("(\\p{L})\\1{2,}");
    private static final Pattern DOUBLED = Pattern.compile("(\\p{L})\\1+");
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");

    private final BanWordValidator words;

    public ChatProfanityValidator() throws IOException {
        Set<String> exact = new HashSet<>();
        try (var reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("moderation/banned-words.txt").getInputStream(),
                StandardCharsets.UTF_8))) {
            reader.lines().map(String::strip)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .forEach(line -> {
                        String term = normalize(line);
                        if (term.isEmpty()) throw new IllegalStateException("Empty banned term");
                        addTerm(exact, term);
                        // Folding single Vietnamese syllables would also ban harmless words:
                        // e.g. "cặc" -> "cac" would match "các", "đụ" -> "du" matches "du lịch".
                        if (term.contains(" ")) addTerm(exact, fold(term));
                    });
        }
        if (exact.isEmpty()) throw new IllegalStateException("Chat word policy is empty");
        words = new BanWordValidator(Set.copyOf(exact), Set.of());
    }

    public void validate(String content) {
        String normalized = normalize(content);
        // Symbols may be separators, letter substitutions, or just inserted noise.
        // Keep actual spaces so unrelated words are never concatenated into a banned word.
        String withoutSymbols = INSERTED_SYMBOLS.matcher(
                Normalizer.normalize(content, Normalizer.Form.NFKC)).replaceAll("");
        if (containsVariant(normalized) || containsVariant(normalize(withoutSymbols))) {
            throw ChatApiException.contentNotAllowed();
        }
    }

    private boolean containsVariant(String normalized) {
        return contains(words, normalized)
                || contains(words, DOUBLED.matcher(normalized).replaceAll("$1"));
    }

    private static void addTerm(Set<String> terms, String term) {
        // Padding enforces word boundaries even though the library matches substrings.
        terms.add(" " + term + " ");
        String compact = term.replace(" ", "");
        if (term.contains(" ")) terms.add(" " + compact + " ");
        String spelled = compact.codePoints()
                .mapToObj(code -> new String(Character.toChars(code)))
                .collect(Collectors.joining(" "));
        terms.add(" " + spelled + " ");
        // Short abbreviations can be split into arbitrary groups: "đ mm", "đm m".
        if (!term.contains(" ") && compact.length() <= 4) {
            for (int mask = 1; mask < (1 << (compact.length() - 1)); mask++) {
                var variant = new StringBuilder();
                for (int i = 0; i < compact.length(); i++) {
                    if (i > 0 && (mask & (1 << (i - 1))) != 0) variant.append(' ');
                    variant.append(compact.charAt(i));
                }
                terms.add(" " + variant + " ");
            }
        }
    }

    private static boolean contains(BanWordValidator validator, String text) {
        return !validator.validate(" " + text + " ", Set.of()).getDetectedBanWords().isEmpty();
    }

    private static String normalize(String text) {
        String value = Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        value = INVISIBLE.matcher(value).replaceAll("");
        value = value.replace('0', 'o').replace('1', 'i').replace('3', 'e')
                .replace('4', 'a').replace('5', 's').replace('7', 't')
                .replace('@', 'a').replace('$', 's').replace('!', 'i');
        value = SEPARATORS.matcher(value).replaceAll(" ").strip();
        return REPEATED.matcher(value).replaceAll("$1");
    }

    private static String fold(String value) {
        return MARKS.matcher(Normalizer.normalize(value, Normalizer.Form.NFD))
                .replaceAll("").replace('đ', 'd');
    }
}

package com.resumeiq.common.text;

import java.util.Locale;

/**
 * The one rule for deciding that two spellings are the same thing.
 *
 * <p>Every part of this product that compares words needs this and needs it to agree with itself.
 * An AI response says "Spring Boot" on one run and "springboot" on the next; a job posting writes
 * "Spring-Boot"; a resume writes "SpringBoot". Stored verbatim those are four skills, and "which
 * skill am I missing most often?" — the question the skill-gap page exists to answer — has no
 * answer at all.
 *
 * <p>It lives here, in {@code common.text}, rather than on the {@code Skill} entity where it
 * started, because the text pipeline needs it and a utility package must not depend on a domain
 * package to borrow a pure function. {@code Skill.slugify} still exists and still means the same
 * thing; it delegates here.
 */
public final class Slug {

    private Slug() {
    }

    /**
     * Reduces any spelling of a term to a lookup key: lower case, hyphen separated, no
     * punctuation.
     *
     * <p>Two deliberate special cases, both of them real skills that a plain "strip the
     * punctuation" rule gets wrong. {@code +} and {@code #} are spelled out, because "C++" and
     * "C#" would otherwise both collapse to {@code "c"} — and a C# developer would be told they
     * are missing C++. A leading dot becomes {@code "dot"}, so ".NET" is {@code "dotnet"} rather
     * than {@code "net"}, while an interior dot stays a separator so "Node.js" is
     * {@code "node-js"}.
     *
     * @param raw any spelling, or null
     * @return the key, or null when given null. Possibly empty, when given only punctuation.
     */
    public static String of(String raw) {
        if (raw == null) {
            return null;
        }
        String lowered = raw.trim().toLowerCase(Locale.ROOT)
                .replace("+", "plus")
                .replace("#", "sharp");
        if (lowered.startsWith(".")) {
            lowered = "dot" + lowered.substring(1);
        }
        String hyphenated = lowered.replaceAll("[^a-z0-9]+", "-");
        return hyphenated.replaceAll("(^-+)|(-+$)", "");
    }
}

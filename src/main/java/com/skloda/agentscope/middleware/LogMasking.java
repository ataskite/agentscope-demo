package com.skloda.agentscope.middleware;

import java.util.regex.Pattern;

/**
 * P2-7: log masking for common PII (Chinese ID card, mobile phone number, email).
 *
 * <p>Audit/metrics middlewares previously wrote full user messages and tool
 * parameters (bank-invoice flow carries name/idCard/phone/email) to INFO logs
 * unmasked, letting PII propagate with the log collection pipeline. Use
 * {@link #mask(String)} on every preview before logging.</p>
 */
public final class LogMasking {

    private static final Pattern ID_CARD = Pattern.compile(
            "(?<![0-9A-Za-z])[1-9]\\d{5}(?:19|20)\\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx](?![0-9A-Za-z])");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    private LogMasking() {
    }

    /** Mask ID numbers, mobile numbers, and emails in the given text. */
    public static String mask(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String out = ID_CARD.matcher(text).replaceAll(m ->
                m.group().substring(0, 4) + "**********" + m.group().substring(m.group().length() - 2));
        out = PHONE.matcher(out).replaceAll(m ->
                m.group().substring(0, 3) + "*****" + m.group().substring(8));
        out = EMAIL.matcher(out).replaceAll(m -> {
            String g = m.group();
            int at = g.indexOf('@');
            return g.charAt(0) + "***" + g.substring(at);
        });
        return out;
    }
}

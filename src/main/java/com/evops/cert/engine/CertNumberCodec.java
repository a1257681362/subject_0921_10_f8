package com.evops.cert.engine;

import com.evops.cert.dto.CertRuleConfig;

/**
 * 证书编号编解码：编号 = 前缀 + 年度 + 批次 + 流水 + 末位校验码。
 *
 * 校验码采用 ISO 7064 MOD 11-2 递推算法（数字 0-9、字母 A-Z 扩展取值 A=10…Z=35），
 * 校验值为 0-9，余数 10 记为 X。手工录入可机读校验，挡住一位抄错与邻位颠倒。
 */
public final class CertNumberCodec {

    private CertNumberCodec() {
    }

    /** 组装完整编号。 */
    public static String format(CertRuleConfig config, int year, String batch, int seq) {
        String body = body(config, year, batch, seq);
        return config.getCheckEnabled() ? body + checkDigit(body) : body;
    }

    /** 编号主体（不含校验码），固定宽度左填零，保证流水段不撞、可按位切分。 */
    public static String body(CertRuleConfig config, int year, String batch, int seq) {
        String yearPart = formatYear(year, config.getYearDigits());
        String batchPart = pad(batch, config.getBatchDigits());
        String seqPart = pad(String.valueOf(seq), config.getSeqDigits());
        return config.getPrefix() + yearPart + batchPart + seqPart;
    }

    private static String formatYear(int year, int yearDigits) {
        String y = String.valueOf(year);
        if (yearDigits == 2) {
            return y.length() >= 2 ? y.substring(y.length() - 2) : pad(y, 2);
        }
        return pad(y, 4);
    }

    private static String pad(String raw, int width) {
        String s = raw == null ? "" : raw;
        if (s.length() > width) {
            throw new IllegalArgumentException("段值超出位宽 " + width + "：" + s);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = s.length(); i < width; i++) {
            sb.append('0');
        }
        return sb.append(s).toString();
    }

    // ---------- 校验码（ISO 7064 MOD 11-2，扩展字母取值，余数 10 记 X） ----------
    // 递推式：S0=0；Si = (S_{i-1} + ai) × 2 mod 11。含校验码整体算完 S=1 即通过。

    /** 返回主体应配的校验码字符（0-9 或 X）。 */
    public static char checkDigit(String body) {
        int s = state(body);
        // 需 (s + c) × 2 ≡ 1 (mod 11)，即 s + c ≡ 6 (mod 11)
        int c = (6 - s + 11) % 11;
        return c == 10 ? 'X' : (char) ('0' + c);
    }

    /** 校验带校验码的完整编号：通过返回 true。 */
    public static boolean isValid(String fullNo) {
        if (fullNo == null || fullNo.length() < 2) {
            return false;
        }
        try {
            int s = 0;
            for (int i = 0; i < fullNo.length(); i++) {
                s = (s + valueOf(fullNo.charAt(i))) * 2 % 11;
            }
            return s == 1;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** 主体（不含校验码）算到的状态。 */
    private static int state(String body) {
        int s = 0;
        for (int i = 0; i < body.length(); i++) {
            s = (s + valueOf(body.charAt(i))) * 2 % 11;
        }
        return s;
    }

    /** 字符取值：0-9 → 0-9，A-Z → 10-35（X 即 10）。 */
    private static int valueOf(char c) {
        char ch = Character.toUpperCase(c);
        if (ch >= '0' && ch <= '9') {
            return ch - '0';
        }
        if (ch >= 'A' && ch <= 'Z') {
            return ch - 'A' + 10;
        }
        throw new IllegalArgumentException("编号含非法字符（仅允许大写字母与数字）：" + c);
    }
}

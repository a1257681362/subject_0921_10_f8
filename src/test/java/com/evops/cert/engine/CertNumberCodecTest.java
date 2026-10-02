package com.evops.cert.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CertNumberCodecTest {

    @Test
    void checkDigitIsConsistentWithValidate() {
        // ISO 7064 MOD 11-2 经典样例：主体 "079" 的校验码应为 4（0794）
        assertEquals('4', CertNumberCodec.checkDigit("079"));
        assertTrue(CertNumberCodec.isValid("0794"));
    }

    @Test
    void detectsSingleDigitError() {
        String body = "ZS20260100001";
        char check = CertNumberCodec.checkDigit(body);
        String full = body + check;
        assertTrue(CertNumberCodec.isValid(full));
        // 抄错流水一位
        assertFalse(CertNumberCodec.isValid("ZS20260100002" + check));
    }

    @Test
    void detectsAdjacentTransposition() {
        String body = "ZS20260100012";
        String full = body + CertNumberCodec.checkDigit(body);
        // 邻位颠倒通常能被检出
        String swapped = "ZS20260100021" + CertNumberCodec.checkDigit("ZS20260100021");
        // 两个不同主体各自合法；直接验证错配不通过
        assertFalse(CertNumberCodec.isValid("ZS20260100021" + full.charAt(full.length() - 1)));
        assertTrue(CertNumberCodec.isValid(swapped));
    }

    @Test
    void rejectsIllegalCharacter() {
        assertFalse(CertNumberCodec.isValid("ZS-2026-X"));
    }
}

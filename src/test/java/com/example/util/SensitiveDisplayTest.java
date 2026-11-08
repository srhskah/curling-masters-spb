package com.example.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class SensitiveDisplayTest {

    @Test
    void maskEmail_keepsShortLocalPrefixAndDomain() {
        Assertions.assertEquals("zhang***@gmail.com", SensitiveDisplay.maskEmail("zhangsan@gmail.com"));
        Assertions.assertEquals("a***@qq.com", SensitiveDisplay.maskEmail("ab@qq.com"));
        Assertions.assertNull(SensitiveDisplay.maskEmail(null));
        Assertions.assertEquals("***", SensitiveDisplay.maskEmail("not-an-email"));
    }
}

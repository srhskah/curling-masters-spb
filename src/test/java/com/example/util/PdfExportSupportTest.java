package com.example.util;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfExportSupportTest {

    @Test
    void buildLogoDataUriReadsStripeLogo() {
        String uri = PdfExportSupport.buildLogoDataUri();
        assertNotNull(uri);
        assertTrue(uri.startsWith("data:image/png;base64,"));
        byte[] png = Base64.getDecoder().decode(uri.substring("data:image/png;base64,".length()));
        assertTrue(png.length > 8);
        assertTrue(png[0] == (byte) 0x89 && png[1] == 'P' && png[2] == 'N' && png[3] == 'G');
    }

    @Test
    void buildHammerIconDataUriIsPng() {
        String uri = PdfExportSupport.buildHammerIconDataUri();
        assertNotNull(uri);
        assertTrue(uri.startsWith("data:image/png;base64,"));
        byte[] png = Base64.getDecoder().decode(uri.substring("data:image/png;base64,".length()));
        assertTrue(png.length > 8);
        assertTrue(png[0] == (byte) 0x89 && png[1] == 'P' && png[2] == 'N' && png[3] == 'G');
    }
}

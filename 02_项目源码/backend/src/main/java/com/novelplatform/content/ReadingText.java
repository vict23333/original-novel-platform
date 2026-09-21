package com.novelplatform.content;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;

/** 段落以空行分隔；内容指纹防止旧正文的位置覆盖新正文进度。 */
public final class ReadingText {
    private ReadingText() {}
    public static String revision(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public static int paragraphCount(String text) {
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        return Math.max(1, (int) Arrays.stream(normalized.split("\n[\t ]*\n"))
                .filter(p -> !p.isBlank()).count());
    }
}

package com.iflytek.skillhub.domain.skill.metadata;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;

/**
 * Extracts the human-readable SkillHub title and summary from a root README.md.
 *
 * <p>The README must begin with a level-one heading followed by a one-line blockquote:
 *
 * <pre>
 * # 中文展示名称
 *
 * &gt; 一句话中文简介。
 * </pre>
 */
public class ReadmePresentationParser {

    static final int MAX_DISPLAY_NAME_LENGTH = 200;
    static final int MAX_SUMMARY_LENGTH = 200;

    public Presentation parse(String content) {
        if (content == null || content.isBlank()) {
            throw invalidFormat();
        }

        String normalizedContent = content.charAt(0) == '\uFEFF' ? content.substring(1) : content;
        String[] lines = normalizedContent.split("\\R", -1);
        int titleLine = firstNonBlankLine(lines, 0);
        if (titleLine < 0 || !lines[titleLine].startsWith("# ")) {
            throw invalidFormat();
        }

        String displayName = lines[titleLine].substring(2).trim();
        int summaryLine = firstNonBlankLine(lines, titleLine + 1);
        if (summaryLine < 0 || !lines[summaryLine].startsWith("> ")) {
            throw invalidFormat();
        }

        String summary = lines[summaryLine].substring(2).trim();
        if (displayName.isBlank()
                || summary.isBlank()
                || displayName.length() > MAX_DISPLAY_NAME_LENGTH
                || summary.length() > MAX_SUMMARY_LENGTH) {
            throw invalidFormat();
        }

        return new Presentation(displayName, summary);
    }

    private int firstNonBlankLine(String[] lines, int start) {
        for (int index = start; index < lines.length; index++) {
            if (!lines[index].isBlank()) {
                return index;
            }
        }
        return -1;
    }

    private DomainBadRequestException invalidFormat() {
        return new DomainBadRequestException(
                "error.skill.publish.readmePresentation.invalid",
                MAX_DISPLAY_NAME_LENGTH,
                MAX_SUMMARY_LENGTH);
    }

    public record Presentation(String displayName, String summary) {
    }
}

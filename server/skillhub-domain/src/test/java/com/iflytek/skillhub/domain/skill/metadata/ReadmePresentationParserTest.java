package com.iflytek.skillhub.domain.skill.metadata;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReadmePresentationParserTest {

    private final ReadmePresentationParser parser = new ReadmePresentationParser();

    @Test
    void parsesFirstHeadingAndFollowingBlockquote() {
        ReadmePresentationParser.Presentation presentation = parser.parse("""
                # 视频抽帧

                > 从本地视频中提取指定画面。

                ## 使用方法
                后续说明。
                """);

        assertEquals("视频抽帧", presentation.displayName());
        assertEquals("从本地视频中提取指定画面。", presentation.summary());
    }

    @Test
    void acceptsUtf8BomAndLeadingBlankLines() {
        ReadmePresentationParser.Presentation presentation = parser.parse(
                "\uFEFF\n# 邮件回复监控\n\n> 检查红人回复并生成待审核草稿。\n");

        assertEquals("邮件回复监控", presentation.displayName());
        assertEquals("检查红人回复并生成待审核草稿。", presentation.summary());
    }

    @Test
    void rejectsReadmeWithoutLevelOneHeading() {
        DomainBadRequestException exception = assertThrows(
                DomainBadRequestException.class,
                () -> parser.parse("## 视频抽帧\n\n> 从视频中提取画面。"));

        assertEquals("error.skill.publish.readmePresentation.invalid", exception.messageCode());
    }

    @Test
    void rejectsReadmeWithoutQuotedSummary() {
        DomainBadRequestException exception = assertThrows(
                DomainBadRequestException.class,
                () -> parser.parse("# 视频抽帧\n\n从视频中提取画面。"));

        assertEquals("error.skill.publish.readmePresentation.invalid", exception.messageCode());
    }

    @Test
    void rejectsPresentationTextOverLengthLimit() {
        String longName = "技".repeat(ReadmePresentationParser.MAX_DISPLAY_NAME_LENGTH + 1);

        DomainBadRequestException exception = assertThrows(
                DomainBadRequestException.class,
                () -> parser.parse("# " + longName + "\n\n> 一句话简介。"));

        assertEquals("error.skill.publish.readmePresentation.invalid", exception.messageCode());
    }
}

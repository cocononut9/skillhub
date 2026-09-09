package com.iflytek.skillhub.domain.skill;

/** Resource distribution mode; fixed for the lifetime of a resource. */
public enum ResourceType {
    SKILL, WEB, PLUGIN, PROMPT;

    public boolean requiresContentScan() {
        return this == PLUGIN || this == PROMPT;
    }
}

package com.iflytek.skillhub.dto;

import java.util.List;

public record DemandSupplementPageResponse(List<DemandSupplementResponse> items, long total, int page, int size) {}

package com.iflytek.skillhub.dto;

import java.util.List;

public record DemandPageResponse(List<DemandResponse> items, long total, int page, int size) {}

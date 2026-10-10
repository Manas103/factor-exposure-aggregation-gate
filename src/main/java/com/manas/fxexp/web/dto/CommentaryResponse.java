package com.manas.fxexp.web.dto;

import java.util.List;

public record CommentaryResponse(String fundId, String commentary, boolean published, List<String> mismatches) {
}

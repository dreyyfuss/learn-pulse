package com.courseservice.dto.request;

import java.util.List;
import java.util.UUID;

public record ReorderModulesRequest(List<ModuleOrderItem> modules) {
    public record ModuleOrderItem(UUID id, int orderIndex) {}
}

package com.example.demo.application.shared.view;

import java.util.List;

public record PageGottenView<T>(
        List<T> content,
        int pageNumber,
        int pageSize,
        long totalElements,
        int totalPages
) {
}
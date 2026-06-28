package gov.brgy.ebms.desktop.api;

import java.util.List;

/**
 * Mirror of Spring Data's Page response shape.
 */
public record Page<T>(
    List<T> content,
    int number,
    int size,
    long totalElements,
    int totalPages,
    boolean first,
    boolean last
) {}

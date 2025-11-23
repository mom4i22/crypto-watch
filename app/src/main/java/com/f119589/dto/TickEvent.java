package com.f119589.dto;

import lombok.Builder;

/**
 * Represents a price tick event from the WebSocket service.
 */
@Builder
public record TickEvent(String symbol, double price) {
}


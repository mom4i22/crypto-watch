package com.f119589.dto;

import lombok.Builder;

@Builder
public record TickEvent(String symbol, double price) {
}


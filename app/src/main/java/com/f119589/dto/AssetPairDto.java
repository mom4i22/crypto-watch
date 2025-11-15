package com.f119589.dto;

public record AssetPairDto(String wsName,
                           String altName,
                           String display,
                           String base,
                           String quote) {
    public String dbSymbol() {
        return wsName;
    }
}

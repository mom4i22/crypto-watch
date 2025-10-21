package com.f119589.dto;

public class AssetPairDto {

    public final String wsName;
    public final String altName;
    public final String display;
    public final String base;
    public final String quote;

    public AssetPairDto(String wsName, String altName, String display, String base, String quote) {
        this.wsName = wsName;
        this.altName = altName;
        this.display = display;
        this.base = base;
        this.quote = quote;
    }

    public String dbSymbol() {
        return wsName;
    }
}

package com.aas.mw.dto.reporting;

public class ReportingColumn {

    private String id = "";
    private String label = "";

    public ReportingColumn() {}

    public ReportingColumn(String id, String label) {
        this.id = id == null ? "" : id;
        this.label = label == null ? "" : label;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id == null ? "" : id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label == null ? "" : label;
    }
}


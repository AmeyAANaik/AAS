package com.aas.mw.dto;

import java.util.List;

public class UserAccessUpdateRequest {

    private List<String> allowFeatures;
    private List<String> denyFeatures;

    public List<String> getAllowFeatures() {
        return allowFeatures;
    }

    public void setAllowFeatures(List<String> allowFeatures) {
        this.allowFeatures = allowFeatures;
    }

    public List<String> getDenyFeatures() {
        return denyFeatures;
    }

    public void setDenyFeatures(List<String> denyFeatures) {
        this.denyFeatures = denyFeatures;
    }
}

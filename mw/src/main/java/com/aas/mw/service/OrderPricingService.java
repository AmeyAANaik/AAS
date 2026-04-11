package com.aas.mw.service;

import org.springframework.stereotype.Service;

@Service
public class OrderPricingService {

    public LinePricing applyMrpCap(double vendorRate, double requestedMarginPercent, Double mrp, String itemLabel) {
        double marginPercent = sanitizeMargin(requestedMarginPercent);
        double sellRate = round(vendorRate * (1 + marginPercent / 100.0));
        double normalizedMrp = mrp == null ? 0.0 : round(mrp);

        if (normalizedMrp <= 0 || vendorRate <= 0) {
            return new LinePricing(sellRate, marginPercent, false);
        }
        if (round(vendorRate) > normalizedMrp) {
            throw new IllegalArgumentException(buildMrpExceededMessage(itemLabel, vendorRate, normalizedMrp));
        }
        if (sellRate <= normalizedMrp) {
            return new LinePricing(sellRate, marginPercent, false);
        }

        double cappedSellRate = normalizedMrp;
        double effectiveMarginPercent = vendorRate <= 0
                ? 0.0
                : round(((cappedSellRate - vendorRate) / vendorRate) * 100.0);
        return new LinePricing(cappedSellRate, effectiveMarginPercent, true);
    }

    private double sanitizeMargin(double marginPercent) {
        double margin = Double.isFinite(marginPercent) ? marginPercent : 0.0;
        if (margin < 0) {
            throw new IllegalArgumentException("margin_percent must be non-negative.");
        }
        return round(margin);
    }

    private String buildMrpExceededMessage(String itemLabel, double vendorRate, double mrp) {
        String label = itemLabel == null || itemLabel.isBlank() ? "item" : itemLabel.trim();
        return "Vendor rate exceeds MRP for " + label + ". Vendor rate=" + round(vendorRate) + ", MRP=" + mrp + ".";
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public record LinePricing(double sellRate, double effectiveMarginPercent, boolean mrpApplied) {
    }
}

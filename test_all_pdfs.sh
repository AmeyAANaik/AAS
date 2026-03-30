#!/bin/bash

TOKEN=$1
VENDOR_ID="FreshHarvest Agro Foods"

echo "Testing all 4 PDFs..."
echo "[" > pdf_diagnostic_report.json

for pdf in DECCAN.pdf Sales3329.pdf Sales_3391.pdf vendor_order.pdf; do
  echo "Processing: $pdf"
  
  curl -s -X POST \
    "http://localhost:8083/api/vendors/$VENDOR_ID/invoice-template/generate-preview" \
    -H "Authorization: Bearer $TOKEN" \
    -F "file=@temp/$pdf" | jq -c '{
      pdf: "'$pdf'",
      parserDetected: .previewMetrics.itemsDetected,
      llmEstimate: .previewMetrics.llmItemCount,
      expectedFromSerials: .completeness.expectedItemCount,
      actualExtracted: (.completeness.extractedSerials | length),
      missingCount: (.completeness.missingSerials | length),
      missingSerials: .completeness.missingSerials,
      ocrLineCount: .previewMetrics.totalRows,
      billAmount: .previewMetrics.billAmount,
      missingItemFields: .missingFields.items,
      previewItemsCount: (.previewItems | length)
    }' >> pdf_diagnostic_report.json
    
  echo "," >> pdf_diagnostic_report.json
  sleep 2
done

echo "]" >> pdf_diagnostic_report.json
echo "Report saved to: pdf_diagnostic_report.json"

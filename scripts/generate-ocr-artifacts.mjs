#!/usr/bin/env node

import { mkdir, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const repoRoot = path.resolve(__dirname, '..');

export const OCR_FIXTURE = {
  company: 'Hotel Sahyadri Pune Pvt Ltd',
  customer: 'Sahyadri All-Day Dining',
  vendor: 'FreshHarvest Agro Foods',
  branchImagePath: path.join(repoRoot, 'images', 'branch_order_sahyadri.svg'),
  vendorPdfPath: path.join(repoRoot, 'images', 'vendor_invoice_freshharvest.pdf')
};

function escapePdfText(value) {
  return String(value ?? '')
    .replace(/\\/g, '\\\\')
    .replace(/\(/g, '\\(')
    .replace(/\)/g, '\\)');
}

function buildPdf(lines) {
  const contentLines = ['BT', '/F1 10 Tf', '48 790 Td'];
  let first = true;
  for (const line of lines) {
    if (!first) {
      contentLines.push('0 -14 Td');
    }
    first = false;
    contentLines.push(`(${escapePdfText(line)}) Tj`);
  }
  contentLines.push('ET');
  const stream = `${contentLines.join('\n')}\n`;
  const objects = [
    '1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n',
    '2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n',
    '3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>\nendobj\n',
    `4 0 obj\n<< /Length ${Buffer.byteLength(stream, 'utf8')} >>\nstream\n${stream}endstream\nendobj\n`,
    '5 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n'
  ];

  let pdf = '%PDF-1.4\n';
  const offsets = [0];
  for (const object of objects) {
    offsets.push(Buffer.byteLength(pdf, 'utf8'));
    pdf += object;
  }
  const xrefOffset = Buffer.byteLength(pdf, 'utf8');
  pdf += `xref\n0 ${objects.length + 1}\n`;
  pdf += '0000000000 65535 f \n';
  for (let index = 1; index < offsets.length; index += 1) {
    pdf += `${String(offsets[index]).padStart(10, '0')} 00000 n \n`;
  }
  pdf += `trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xrefOffset}\n%%EOF\n`;
  return Buffer.from(pdf, 'utf8');
}

function buildBranchSvg() {
  return `<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="1080" height="1440" viewBox="0 0 1080 1440">
  <defs>
    <linearGradient id="bg" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#efe5d5"/>
      <stop offset="100%" stop-color="#d7c3a4"/>
    </linearGradient>
    <filter id="shadow" x="-20%" y="-20%" width="140%" height="140%">
      <feDropShadow dx="0" dy="18" stdDeviation="18" flood-color="#4a3b2a" flood-opacity="0.2"/>
    </filter>
  </defs>
  <rect width="1080" height="1440" fill="url(#bg)"/>
  <rect x="84" y="108" width="912" height="1200" rx="36" fill="#efece4" filter="url(#shadow)"/>
  <rect x="84" y="108" width="912" height="92" rx="36" fill="#1f6a5f"/>
  <circle cx="154" cy="154" r="26" fill="#f8d677"/>
  <text x="196" y="165" font-family="Helvetica, Arial, sans-serif" font-size="30" fill="#ffffff">Fortune Hills Banquet Hall Procurement</text>
  <text x="142" y="248" font-family="Helvetica, Arial, sans-serif" font-size="26" fill="#6a6258">02 Mar 2026, 8:10 PM</text>
  <rect x="132" y="292" width="816" height="902" rx="28" fill="#fffdf8"/>
  <text x="162" y="354" font-family="Helvetica, Arial, sans-serif" font-size="28" fill="#2c2c2c">Wedding weekend dispatch for 180 covers</text>
  <text x="162" y="404" font-family="Helvetica, Arial, sans-serif" font-size="25" fill="#4f4f4f">1. Potato - 50 kg sack x 6</text>
  <text x="162" y="450" font-family="Helvetica, Arial, sans-serif" font-size="25" fill="#4f4f4f">2. Onion - 50 kg sack x 5</text>
  <text x="162" y="496" font-family="Helvetica, Arial, sans-serif" font-size="25" fill="#4f4f4f">3. Tomato - 20 kg crate x 7</text>
  <text x="162" y="542" font-family="Helvetica, Arial, sans-serif" font-size="25" fill="#4f4f4f">4. Basmati Rice 1121 - 25 kg bag x 8</text>
  <text x="162" y="588" font-family="Helvetica, Arial, sans-serif" font-size="25" fill="#4f4f4f">5. Paneer - 1 kg block x 18</text>
  <text x="162" y="634" font-family="Helvetica, Arial, sans-serif" font-size="25" fill="#4f4f4f">6. Full Cream Milk - 1 L x 36</text>
  <text x="162" y="680" font-family="Helvetica, Arial, sans-serif" font-size="25" fill="#4f4f4f">7. Coriander Leaves - 1 kg x 8</text>
  <text x="162" y="726" font-family="Helvetica, Arial, sans-serif" font-size="25" fill="#4f4f4f">8. Paper Food Container 750 ml x 120</text>
  <text x="162" y="792" font-family="Helvetica, Arial, sans-serif" font-size="26" fill="#2c2c2c">Delivery needed before 11 AM tomorrow.</text>
  <text x="162" y="838" font-family="Helvetica, Arial, sans-serif" font-size="26" fill="#2c2c2c">Chef Arvind has approved the quantities.</text>
  <text x="162" y="904" font-family="Helvetica, Arial, sans-serif" font-size="23" fill="#7a6d5e">Sent from WhatsApp image shared in Fortune Hills Ops group</text>
  <text x="162" y="1096" font-family="Helvetica, Arial, sans-serif" font-size="22" fill="#7a6d5e">Seeded OCR fixture for AAS branch-image flow</text>
</svg>
`;
}

export async function generateOcrArtifacts() {
  await mkdir(path.dirname(OCR_FIXTURE.branchImagePath), { recursive: true });
  await writeFile(OCR_FIXTURE.branchImagePath, buildBranchSvg(), 'utf8');

  const pdfLines = [
    'FreshHarvest Agro Foods',
    'Plot 14, Market Yard Annex, Gultekdi, Pune 411037',
    'GSTIN: 27AAECF4832M1ZU   Phone: +91 98220 44187',
    'Invoice No: FHAF/26-27/0184   Invoice Date: 2026-03-02',
    'Bill To: Hotel Sahyadri Pune Pvt Ltd - Sahyadri All-Day Dining',
    '------------------------------------------------------------------',
    'Item Description                Qty     Rate      Amount    HSN',
    'Potato - 50 kg sack            3       1325.00   3975.00   0701',
    'Onion - 50 kg sack             2       1480.00   2960.00   0703',
    'Tomato - 20 kg crate           4        620.00   2480.00   0702',
    'Coriander Leaves - 1 kg        6         85.00    510.00   0709',
    'Green Chilli - 1 kg            5         96.00    480.00   0904',
    'Subtotal                                      10405.00',
    'CGST 2.5%                                       260.13',
    'SGST 2.5%                                       260.13',
    'Grand Total                                  10925.26',
    'Thank you for your continued business.'
  ];
  await writeFile(OCR_FIXTURE.vendorPdfPath, buildPdf(pdfLines));
}

if (process.argv[1] === __filename) {
  generateOcrArtifacts().catch(err => {
    console.error(err?.stack || err?.message || err);
    process.exitCode = 1;
  });
}

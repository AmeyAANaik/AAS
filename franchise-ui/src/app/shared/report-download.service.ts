import { Injectable } from '@angular/core';

export type ReportExportFormat = 'csv' | 'xlsx' | 'pdf';

export interface ReportColumn {
  key: string;
  label: string;
  align?: 'left' | 'right';
}

export interface ReportSummary {
  label: string;
  value: string | number;
}

export interface ReportDownloadInput {
  title: string;
  subtitle?: string;
  period?: string;
  fileName: string;
  columns: ReportColumn[];
  rows: Array<Record<string, unknown>>;
  summary?: ReportSummary[];
}

@Injectable({ providedIn: 'root' })
export class ReportDownloadService {
  download(report: ReportDownloadInput, format: ReportExportFormat): void {
    const safeName = this.fileSegment(report.fileName);
    if (format === 'csv') {
      this.saveBlob(this.csvBlob(report), `${safeName}.csv`);
      return;
    }
    if (format === 'xlsx') {
      this.saveBlob(this.excelBlob(report), `${safeName}.xls`);
      return;
    }
    this.saveBlob(this.pdfBlob(report), `${safeName}.pdf`);
  }

  private csvBlob(report: ReportDownloadInput): Blob {
    const lines = [
      [report.title],
      report.subtitle ? [report.subtitle] : [],
      report.period ? ['Period', report.period] : [],
      ['Generated at', this.generatedAt()],
      [],
      ...(report.summary?.map(row => [row.label, row.value]) ?? []),
      report.summary?.length ? [] : [],
      report.columns.map(col => col.label),
      ...report.rows.map(row => report.columns.map(col => this.cell(row[col.key])))
    ].filter(row => row.length);
    const csv = lines.map(row => row.map(cell => this.csvCell(cell)).join(',')).join('\r\n');
    return new Blob([csv], { type: 'text/csv;charset=utf-8' });
  }

  private excelBlob(report: ReportDownloadInput): Blob {
    const summaryRows = report.summary?.map(row => `
      <tr><th>${this.html(row.label)}</th><td>${this.html(row.value)}</td></tr>
    `).join('') ?? '';
    const headerCells = report.columns.map(col => `<th>${this.html(col.label)}</th>`).join('');
    const bodyRows = report.rows.map(row => `
      <tr>${report.columns.map(col => `<td class="${col.align === 'right' ? 'num' : ''}">${this.html(this.cell(row[col.key]))}</td>`).join('')}</tr>
    `).join('');
    const html = `
      <!doctype html>
      <html>
        <head>
          <meta charset="utf-8">
          <style>
            body { font-family: Arial, sans-serif; color: #1f2933; }
            .doc-title { font-size: 22px; font-weight: 700; }
            .doc-meta { color: #64748b; margin: 4px 0 16px; }
            table { border-collapse: collapse; width: 100%; }
            th { background: #f1f5f9; text-align: left; }
            th, td { border: 1px solid #d9e2ec; padding: 8px; }
            .num { text-align: right; }
          </style>
        </head>
        <body>
          <div class="doc-title">${this.html(report.title)}</div>
          <div class="doc-meta">${this.html(report.subtitle ?? '')}</div>
          <table>${summaryRows}<tr><th>Period</th><td>${this.html(report.period ?? 'All')}</td></tr><tr><th>Generated at</th><td>${this.html(this.generatedAt())}</td></tr></table>
          <br>
          <table><thead><tr>${headerCells}</tr></thead><tbody>${bodyRows}</tbody></table>
        </body>
      </html>
    `;
    return new Blob([html], { type: 'application/vnd.ms-excel;charset=utf-8' });
  }

  private pdfBlob(report: ReportDownloadInput): Blob {
    const pageWidth = 595;
    const pageHeight = 842;
    const left = 42;
    const bottom = 44;
    const lineHeight = 14;
    const maxChars = 92;
    const objects: string[] = [
      '',
      '',
      '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
      '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>'
    ];
    const pages: number[] = [];
    let pageLines: string[] = [];
    let y = 802;

    const addObject = (content: string): number => {
      objects.push(content);
      return objects.length;
    };

    const addPage = () => {
      const content = pageLines.join('\n');
      const contentObj = addObject(`<< /Length ${content.length} >>\nstream\n${content}\nendstream`);
      const pageObj = addObject(`<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${pageWidth} ${pageHeight}] /Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> /Contents ${contentObj} 0 R >>`);
      pages.push(pageObj);
      pageLines = [];
      y = 802;
    };
    const ensureSpace = (needed = lineHeight) => {
      if (y - needed < bottom) {
        addPage();
      }
    };
    const text = (value: string, x = left, size = 10, font = 'F1') => {
      ensureSpace(lineHeight);
      pageLines.push(`BT /${font} ${size} Tf ${x} ${y} Td (${this.pdfText(value)}) Tj ET`);
      y -= lineHeight;
    };
    const rule = () => {
      ensureSpace(10);
      pageLines.push(`0.74 w ${left} ${y + 5} m ${pageWidth - left} ${y + 5} l S`);
      y -= 8;
    };

    text('Franchise Console', left, 15, 'F2');
    text(report.title, left, 18, 'F2');
    if (report.subtitle) {
      this.wrap(report.subtitle, maxChars).forEach(line => text(line, left, 10));
    }
    text(`Document: ${this.documentNo(report.fileName)}`);
    text(`Period: ${report.period ?? 'All'}`);
    text(`Generated at: ${this.generatedAt()}`);
    rule();

    (report.summary ?? []).forEach(row => text(`${row.label}: ${this.cell(row.value)}`, left, 10, 'F2'));
    if (report.summary?.length) {
      rule();
    }

    const header = report.columns.map(col => col.label).join(' | ');
    this.wrap(header, maxChars).forEach(line => text(line, left, 9, 'F2'));
    rule();
    report.rows.forEach(row => {
      const line = report.columns.map(col => this.cell(row[col.key])).join(' | ');
      this.wrap(line, maxChars).forEach(wrapped => text(wrapped, left, 8));
    });

    addPage();
    objects[0] = '<< /Type /Catalog /Pages 2 0 R >>';
    objects[1] = `<< /Type /Pages /Kids [${pages.map(id => `${id} 0 R`).join(' ')}] /Count ${pages.length} >>`;

    const normalizedObjects = objects.map((obj, index) => `${index + 1} 0 obj\n${obj}\nendobj\n`);
    let pdf = '%PDF-1.4\n';
    const offsets: number[] = [0];
    normalizedObjects.forEach(obj => {
      offsets.push(pdf.length);
      pdf += obj;
    });
    const xrefAt = pdf.length;
    pdf += `xref\n0 ${normalizedObjects.length + 1}\n0000000000 65535 f \n`;
    offsets.slice(1).forEach(offset => {
      pdf += `${String(offset).padStart(10, '0')} 00000 n \n`;
    });
    pdf += `trailer\n<< /Size ${normalizedObjects.length + 1} /Root 1 0 R >>\nstartxref\n${xrefAt}\n%%EOF`;
    return new Blob([pdf], { type: 'application/pdf' });
  }

  private saveBlob(blob: Blob, fileName: string): void {
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = fileName;
    link.click();
    window.URL.revokeObjectURL(url);
  }

  private cell(value: unknown): string {
    if (value === null || value === undefined || value === '') {
      return '-';
    }
    return String(value);
  }

  private csvCell(value: unknown): string {
    return `"${this.cell(value).replace(/"/g, '""')}"`;
  }

  private html(value: unknown): string {
    return this.cell(value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  private pdfText(value: string): string {
    return value.replace(/\\/g, '\\\\').replace(/\(/g, '\\(').replace(/\)/g, '\\)').replace(/[^\x20-\x7E]/g, '-');
  }

  private wrap(value: string, max: number): string[] {
    const words = value.split(/\s+/);
    const lines: string[] = [];
    let current = '';
    words.forEach(word => {
      const next = current ? `${current} ${word}` : word;
      if (next.length > max && current) {
        lines.push(current);
        current = word;
      } else {
        current = next;
      }
    });
    if (current) {
      lines.push(current);
    }
    return lines.length ? lines : ['-'];
  }

  private generatedAt(): string {
    return new Date().toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' });
  }

  private documentNo(seed: string): string {
    const stamp = new Date().toISOString().replace(/[-:T.Z]/g, '').slice(0, 12);
    return `RPT-${this.fileSegment(seed).toUpperCase()}-${stamp}`;
  }

  private fileSegment(value: string): string {
    return value.trim().toLowerCase().replace(/[^a-z0-9._-]+/g, '-') || 'report';
  }
}

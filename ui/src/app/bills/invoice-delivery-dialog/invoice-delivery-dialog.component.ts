import { Component, Inject, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { finalize } from 'rxjs/operators';
import { formatUiError } from '../../shared/error-message.util';
import { BillsService } from '../bills.service';
import { InvoiceDeliveryChannelPreview, InvoiceDeliveryPreview, InvoiceView } from '../bills.model';

export interface InvoiceDeliveryDialogData {
  channel: 'email' | 'whatsapp';
  invoice: InvoiceView;
}

export interface InvoiceDeliveryDialogResult {
  sent: boolean;
  message?: string;
}

@Component({
  selector: 'app-invoice-delivery-dialog',
  templateUrl: './invoice-delivery-dialog.component.html',
  styleUrl: './invoice-delivery-dialog.component.scss'
})
export class InvoiceDeliveryDialogComponent implements OnInit {
  preview: InvoiceDeliveryPreview | null = null;
  isLoading = true;
  isSending = false;
  errorMessage = '';
  form: FormGroup = this.fb.group({
    recipient: ['', [Validators.required]],
    message: ['', [Validators.required, Validators.maxLength(2000)]]
  });

  constructor(
    private readonly dialogRef: MatDialogRef<InvoiceDeliveryDialogComponent, InvoiceDeliveryDialogResult>,
    @Inject(MAT_DIALOG_DATA) public readonly data: InvoiceDeliveryDialogData,
    private readonly billsService: BillsService,
    private readonly fb: FormBuilder
  ) {}

  ngOnInit(): void {
    this.billsService.getInvoiceDeliveryPreview(this.data.invoice.id)
      .pipe(finalize(() => (this.isLoading = false)))
      .subscribe({
        next: preview => {
          this.preview = preview;
          const channel = this.activeChannel;
          this.form.patchValue({
            recipient: channel.recipient,
            message: this.data.channel === 'email' ? preview.defaultEmailMessage : preview.defaultWhatsAppMessage
          });
          if (!channel.configured || channel.missingRecipient) {
            this.form.markAllAsTouched();
          }
        },
        error: err => {
          this.errorMessage = formatUiError(err, 'Unable to load invoice delivery details.');
        }
      });
  }

  send(): void {
    if (this.form.invalid || !this.preview) {
      this.form.markAllAsTouched();
      return;
    }
    this.isSending = true;
    this.errorMessage = '';
    const payload = {
      recipient: String(this.form.get('recipient')?.value ?? '').trim(),
      message: String(this.form.get('message')?.value ?? '').trim()
    };
    const request$ = this.data.channel === 'email'
      ? this.billsService.sendInvoiceEmail(this.data.invoice.id, payload)
      : this.billsService.sendInvoiceWhatsApp(this.data.invoice.id, payload);
    request$
      .pipe(finalize(() => (this.isSending = false)))
      .subscribe({
        next: response => {
          this.dialogRef.close({
            sent: true,
            message: String(response['message'] ?? `${this.channelLabel} sent.`)
          });
        },
        error: err => {
          this.errorMessage = formatUiError(err, `Unable to send invoice via ${this.channelLabel.toLowerCase()}.`);
        }
      });
  }

  close(): void {
    this.dialogRef.close({ sent: false });
  }

  get channelLabel(): string {
    return this.data.channel === 'email' ? 'Email' : 'WhatsApp';
  }

  get activeChannel(): InvoiceDeliveryChannelPreview {
    if (!this.preview) {
      return {
        channel: this.data.channel,
        recipient: '',
        configured: false,
        missingRecipient: true,
        hint: ''
      };
    }
    return this.data.channel === 'email' ? this.preview.email : this.preview.whatsapp;
  }
}

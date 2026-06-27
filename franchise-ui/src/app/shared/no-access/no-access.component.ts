import { Component } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-no-access',
  standalone: true,
  imports: [MatIconModule],
  template: `
    <div class="berry-panel no-access">
      <mat-icon aria-hidden="true">lock</mat-icon>
      <h2>No modules available</h2>
      <p>Your role and the current module configuration don't grant access to any workspace. Ask a Super Admin to enable a module or adjust your access.</p>
    </div>
  `,
  styles: [`
    .no-access { display: grid; gap: 10px; justify-items: center; text-align: center; padding: 48px 24px; }
    .no-access .mat-icon { font-size: 40px; width: 40px; height: 40px; color: var(--muted); }
    .no-access p { max-width: 420px; color: var(--muted); font-size: 14px; }
  `]
})
export class NoAccessComponent {}

import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { PageHeaderComponent } from '../page-header/page-header.component';

@Component({
  selector: 'app-coming-soon',
  standalone: true,
  imports: [CommonModule, MatIconModule, PageHeaderComponent],
  templateUrl: './coming-soon.component.html',
  styleUrl: './coming-soon.component.css'
})
export class ComingSoonComponent {
  title = 'Coming soon';
  kicker = 'Module';
  description = 'This module is planned for a future release.';
  features: string[] = [];

  constructor(route: ActivatedRoute) {
    const data = route.snapshot.data;
    this.title = data['title'] ?? this.title;
    this.kicker = data['kicker'] ?? this.kicker;
    this.description = data['description'] ?? this.description;
    this.features = data['features'] ?? [];
  }
}

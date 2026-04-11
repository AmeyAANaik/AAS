import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { RouterModule } from '@angular/router';
import { BerryThemeService } from './shared/services/berry-theme.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent implements OnInit {
  constructor(private themeService: BerryThemeService) {}

  ngOnInit(): void {
    // Initialize theme service - this will load the saved theme preference
    // from localStorage on app startup
  }
}

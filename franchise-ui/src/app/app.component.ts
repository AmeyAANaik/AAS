import { Component } from '@angular/core';
import { RouterModule } from '@angular/router';
import { BerryThemeService } from './shared/services/berry-theme.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterModule],
  template: '<router-outlet></router-outlet>'
})
export class AppComponent {
  // Injecting the theme service applies the saved theme on startup.
  constructor(private themeService: BerryThemeService) {}
}

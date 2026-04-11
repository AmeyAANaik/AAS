import { NgModule } from '@angular/core';
import { BerryAASThemeConfig } from './berry-aas.config';

@NgModule({
  declarations: [],
  imports: [],
  providers: [
    {
      provide: 'BERRY_THEME_CONFIG',
      useValue: BerryAASThemeConfig,
    },
  ],
})
export class BerryAASModule {}

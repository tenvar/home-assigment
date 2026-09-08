import { registerLocaleData } from '@angular/common';
import localeBe from '@angular/common/locales/be';
import { provideHttpClient } from '@angular/common/http';
import { LOCALE_ID } from '@angular/core';
import { bootstrapApplication } from '@angular/platform-browser';
import { AppComponent } from './app/app.component';

registerLocaleData(localeBe);
bootstrapApplication(AppComponent, {
  providers: [provideHttpClient(), { provide: LOCALE_ID, useValue: document.documentElement.lang }]
}).catch(error => console.error(error));

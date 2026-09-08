import '@angular/localize/init';
import { loadTranslations } from '@angular/localize';
import translations from './locale/be.json';

const language = location.pathname.startsWith('/be') ? 'be' : 'en';
document.documentElement.lang = language;
if (language === 'be') loadTranslations(translations);
import('./bootstrap').catch(error => console.error(error));

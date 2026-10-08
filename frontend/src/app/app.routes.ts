import { Routes } from '@angular/router';
import { InsightsPage } from './features/insights/insights-page';
import { LeadsPage } from './features/leads/leads-page';
import { TodayPage } from './features/today/today-page';

export const routes: Routes = [
  { path: '', component: LeadsPage, title: 'LeadLens' },
  { path: 'today', component: TodayPage, title: 'Today · LeadLens' },
  { path: 'insights', component: InsightsPage, title: 'Insights · LeadLens' },
  { path: '**', redirectTo: '' },
];

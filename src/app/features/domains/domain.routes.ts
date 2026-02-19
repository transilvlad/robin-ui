import { Routes } from '@angular/router';
import { DomainListComponent } from './components/domain-list/domain-list.component';
import { DomainWizardComponent } from './components/domain-wizard/domain-wizard.component';
import { DomainDetailComponent } from './components/domain-detail/domain-detail.component';

export const DOMAIN_ROUTES: Routes = [
  { path: '', component: DomainListComponent },
  { path: 'new', component: DomainWizardComponent },
  { path: ':id', component: DomainDetailComponent }
];

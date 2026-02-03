import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Routes } from '@angular/router';
import { ReactiveFormsModule, FormsModule } from '@angular/forms';
import { DomainListComponent } from './components/domain-list/domain-list.component';
import { DomainWizardComponent } from './components/domain-wizard/domain-wizard.component';
import { DomainDetailComponent } from './components/domain-detail/domain-detail.component';

const routes: Routes = [
  { path: '', component: DomainListComponent },
  { path: 'new', component: DomainWizardComponent },
  { path: ':id', component: DomainDetailComponent }
];

@NgModule({
  declarations: [
    DomainListComponent,
    DomainWizardComponent,
    DomainDetailComponent
  ],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    FormsModule,
    RouterModule.forChild(routes)
  ]
})
export class DomainModule { }

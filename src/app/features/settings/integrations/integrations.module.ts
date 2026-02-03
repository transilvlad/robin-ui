import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Routes } from '@angular/router';
import { ReactiveFormsModule, FormsModule } from '@angular/forms';
import { ProvidersListComponent } from './providers/providers-list.component';

const routes: Routes = [
  { path: 'providers', component: ProvidersListComponent }
];

@NgModule({
  declarations: [
    ProvidersListComponent
  ],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    FormsModule,
    RouterModule.forChild(routes)
  ]
})
export class IntegrationsModule { }
import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { QueueListComponent } from './queue/queue-list.component';
import { StorageBrowserComponent } from './storage/storage-browser.component';

const routes: Routes = [
  {
    path: 'queue',
    component: QueueListComponent,
  },
  {
    path: 'storage',
    component: StorageBrowserComponent,
  },
  {
    path: '',
    redirectTo: 'queue',
    pathMatch: 'full',
  },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class EmailRoutingModule {}

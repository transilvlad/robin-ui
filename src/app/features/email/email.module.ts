import { NgModule } from '@angular/core';
import { SharedModule } from '@shared/shared.module';
import { EmailRoutingModule } from './email-routing.module';
import { QueueListComponent } from './queue/queue-list.component';
import { StorageBrowserComponent } from './storage/storage-browser.component';
import { QueueService } from './services/queue.service';
import { StorageService } from './services/storage.service';

@NgModule({
  declarations: [QueueListComponent, StorageBrowserComponent],
  imports: [SharedModule, EmailRoutingModule],
  providers: [QueueService, StorageService],
})
export class EmailModule {}

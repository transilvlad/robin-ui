import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';

// Components
import { HeaderComponent } from './components/header/header.component';
import { SidebarComponent } from './components/sidebar/sidebar.component';
import { StatusBadgeComponent } from './components/status-badge/status-badge.component';

// Pipes
import { BytesPipe } from './pipes/bytes.pipe';
import { RelativeTimePipe } from './pipes/relative-time.pipe';

const components = [HeaderComponent, SidebarComponent, StatusBadgeComponent];

const pipes = [BytesPipe, RelativeTimePipe];

@NgModule({
  declarations: [...components, ...pipes],
  imports: [CommonModule, RouterModule, FormsModule, ReactiveFormsModule],
  exports: [
    CommonModule,
    RouterModule,
    FormsModule,
    ReactiveFormsModule,
    ...components,
    ...pipes,
  ],
})
export class SharedModule {}

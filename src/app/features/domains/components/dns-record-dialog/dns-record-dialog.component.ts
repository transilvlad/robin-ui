import { Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { DnsRecord } from '@core/services/domain.service';

@Component({
  selector: 'app-dns-record-dialog',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, MatDialogModule, MatButtonModule],
  template: `
    <div class="p-6 space-y-6">
      <div class="flex flex-col gap-2">
        <h2 class="text-xl font-semibold tracking-tight">Edit DNS Record</h2>
        <p class="text-sm text-muted-foreground">Modify DNS record configuration.</p>
      </div>

      <form [formGroup]="form" (ngSubmit)="onSubmit()" class="space-y-4">
        <div class="grid grid-cols-2 gap-4">
          <div class="space-y-2">
            <label class="text-sm font-medium leading-none">Type</label>
            <select class="input" formControlName="type">
              <option value="A">A</option>
              <option value="AAAA">AAAA</option>
              <option value="CNAME">CNAME</option>
              <option value="MX">MX</option>
              <option value="TXT">TXT</option>
              <option value="SRV">SRV</option>
              <option value="TLSA">TLSA</option>
            </select>
          </div>
          <div class="space-y-2">
            <label class="text-sm font-medium leading-none">TTL</label>
            <input class="input" type="number" formControlName="ttl">
          </div>
        </div>

        <div class="space-y-2">
          <label class="text-sm font-medium leading-none">Name</label>
          <input class="input" formControlName="name" placeholder="@ or subdomain">
        </div>

        <div class="space-y-2">
          <label class="text-sm font-medium leading-none">Content</label>
          <textarea class="input min-h-[80px]" formControlName="content"></textarea>
        </div>

        <div class="grid grid-cols-2 gap-4" *ngIf="form.get('type')?.value === 'MX'">
          <div class="space-y-2">
            <label class="text-sm font-medium leading-none">Priority</label>
            <input class="input" type="number" formControlName="priority">
          </div>
        </div>

        <div class="flex justify-end gap-3 pt-4">
          <button type="button" class="btn btn-outline btn-sm" (click)="dialogRef.close()">Cancel</button>
          <button type="submit" class="btn btn-primary btn-sm" [disabled]="form.invalid">Update Record</button>
        </div>
      </form>
    </div>
  `
})
export class DnsRecordDialogComponent {
  form: FormGroup;

  constructor(
    private fb: FormBuilder,
    public dialogRef: MatDialogRef<DnsRecordDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: DnsRecord
  ) {
    this.form = this.fb.group({
      type: [data.type, Validators.required],
      name: [data.name, Validators.required],
      content: [data.content, Validators.required],
      ttl: [data.ttl || 300, Validators.required],
      priority: [data.priority || 10],
      purpose: [data.purpose]
    });
  }

  onSubmit(): void {
    if (this.form.valid) {
      this.dialogRef.close(this.form.value);
    }
  }
}

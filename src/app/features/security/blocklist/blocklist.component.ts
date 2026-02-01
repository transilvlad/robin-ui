import { Component, OnInit, inject, ViewChild } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatPaginator, PageEvent } from '@angular/material/paginator';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatDialog } from '@angular/material/dialog';
import { SecurityService } from '../../../core/services/security.service';
import {
  BlocklistEntry,
  CreateBlocklistEntry,
  BlocklistEntryType,
  validateBlocklistValue,
} from '../../../core/models/security.model';

@Component({
  selector: 'app-blocklist',
  templateUrl: './blocklist.component.html',
  styleUrls: ['./blocklist.component.scss'],
  standalone: false
})
export class BlocklistComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly securityService = inject(SecurityService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly dialog = inject(MatDialog);

  @ViewChild(MatPaginator) paginator!: MatPaginator;

  entries: BlocklistEntry[] = [];
  displayedColumns = ['type', 'value', 'reason', 'createdAt', 'expiresAt', 'active', 'actions'];

  loading = false;
  totalEntries = 0;
  pageSize = 25;
  currentPage = 0;

  entryForm!: FormGroup;
  showAddForm = false;
  editingEntry: BlocklistEntry | null = null;

  // Filter options
  filterType: string = '';
  filterActive: boolean | null = null;

  // Entry types
  entryTypes = Object.values(BlocklistEntryType);
  BlocklistEntryType = BlocklistEntryType;

  ngOnInit(): void {
    this.initializeForm();
    this.loadEntries();
  }

  private initializeForm(): void {
    this.entryForm = this.fb.group({
      type: [BlocklistEntryType.IP, Validators.required],
      value: ['', [Validators.required, Validators.minLength(1)]],
      reason: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(500)]],
      expiresAt: [null],
      active: [true],
    });

    // Add custom validator for value based on type
    this.entryForm.get('type')?.valueChanges.subscribe(() => {
      this.entryForm.get('value')?.updateValueAndValidity();
    });
  }

  loadEntries(): void {
    this.loading = true;

    const params: any = {
      page: this.currentPage,
      limit: this.pageSize,
    };

    if (this.filterType) {
      params.type = this.filterType;
    }

    if (this.filterActive !== null) {
      params.active = this.filterActive;
    }

    this.securityService.getBlocklistEntries(params).subscribe({
      next: (response) => {
        this.entries = response.items;
        this.totalEntries = response.total;
        this.loading = false;
      },
      error: (error) => {
        console.error('Failed to load blocklist entries:', error);
        this.snackBar.open('Failed to load blocklist entries', 'Close', {
          duration: 5000,
          panelClass: ['error-snackbar']
        });
        this.loading = false;
      }
    });
  }

  onPageChange(event: PageEvent): void {
    this.currentPage = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadEntries();
  }

  applyFilters(): void {
    this.currentPage = 0;
    this.loadEntries();
  }

  clearFilters(): void {
    this.filterType = '';
    this.filterActive = null;
    this.applyFilters();
  }

  toggleAddForm(): void {
    this.showAddForm = !this.showAddForm;
    if (!this.showAddForm) {
      this.entryForm.reset({
        type: BlocklistEntryType.IP,
        active: true,
      });
      this.editingEntry = null;
    }
  }

  addEntry(): void {
    if (this.entryForm.invalid) {
      this.snackBar.open('Please fix form errors', 'Close', {
        duration: 3000,
        panelClass: ['error-snackbar']
      });
      return;
    }

    const formValue = this.entryForm.value;

    // Validate the value based on type
    if (!validateBlocklistValue(formValue.type, formValue.value)) {
      this.snackBar.open(`Invalid ${formValue.type.toLowerCase()} format`, 'Close', {
        duration: 5000,
        panelClass: ['error-snackbar']
      });
      return;
    }

    const entry: CreateBlocklistEntry = {
      type: formValue.type,
      value: formValue.value,
      reason: formValue.reason,
      expiresAt: formValue.expiresAt ? new Date(formValue.expiresAt).toISOString() : null,
      active: formValue.active ?? true,
    };

    this.securityService.createBlocklistEntry(entry).subscribe({
      next: (created) => {
        this.snackBar.open('✓ Blocklist entry added successfully', 'Close', {
          duration: 3000,
          panelClass: ['success-snackbar']
        });
        this.loadEntries();
        this.toggleAddForm();
      },
      error: (error) => {
        this.snackBar.open(
          `✗ Failed to add entry: ${error.message || 'Unknown error'}`,
          'Close',
          {
            duration: 7000,
            panelClass: ['error-snackbar']
          }
        );
      }
    });
  }

  deleteEntry(entry: BlocklistEntry): void {
    if (!entry.id) return;

    const confirmed = confirm(
      `Are you sure you want to delete this blocklist entry?\n\nType: ${entry.type}\nValue: ${entry.value}\nReason: ${entry.reason}`
    );

    if (!confirmed) return;

    this.securityService.deleteBlocklistEntry(entry.id).subscribe({
      next: () => {
        this.snackBar.open('✓ Blocklist entry deleted successfully', 'Close', {
          duration: 3000,
          panelClass: ['success-snackbar']
        });
        this.loadEntries();
      },
      error: (error) => {
        this.snackBar.open(
          `✗ Failed to delete entry: ${error.message || 'Unknown error'}`,
          'Close',
          {
            duration: 7000,
            panelClass: ['error-snackbar']
          }
        );
      }
    });
  }

  toggleActive(entry: BlocklistEntry): void {
    if (!entry.id) return;

    const newStatus = !entry.active;

    this.securityService.updateBlocklistEntry(entry.id, { active: newStatus }).subscribe({
      next: (updated) => {
        this.snackBar.open(
          `✓ Entry ${newStatus ? 'activated' : 'deactivated'} successfully`,
          'Close',
          {
            duration: 3000,
            panelClass: ['success-snackbar']
          }
        );
        this.loadEntries();
      },
      error: (error) => {
        this.snackBar.open(
          `✗ Failed to update entry: ${error.message || 'Unknown error'}`,
          'Close',
          {
            duration: 7000,
            panelClass: ['error-snackbar']
          }
        );
      }
    });
  }

  exportBlocklist(format: 'csv' | 'json'): void {
    this.securityService.exportBlocklist(format).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `blocklist-${new Date().toISOString().split('T')[0]}.${format}`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        window.URL.revokeObjectURL(url);

        this.snackBar.open(`✓ Blocklist exported as ${format.toUpperCase()}`, 'Close', {
          duration: 3000,
          panelClass: ['success-snackbar']
        });
      },
      error: (error) => {
        this.snackBar.open(
          `✗ Failed to export: ${error.message || 'Unknown error'}`,
          'Close',
          {
            duration: 7000,
            panelClass: ['error-snackbar']
          }
        );
      }
    });
  }

  onFileSelected(event: any): void {
    const file: File = event.target.files[0];
    if (!file) return;

    // Validate file type
    const allowedTypes = ['text/csv', 'application/json'];
    if (!allowedTypes.includes(file.type) && !file.name.endsWith('.csv') && !file.name.endsWith('.json')) {
      this.snackBar.open('Only CSV and JSON files are supported', 'Close', {
        duration: 5000,
        panelClass: ['error-snackbar']
      });
      return;
    }

    this.securityService.importBlocklist(file).subscribe({
      next: (result) => {
        let message = `✓ Import complete: ${result.imported} entries added`;
        if (result.failed > 0) {
          message += `, ${result.failed} failed`;
        }

        this.snackBar.open(message, 'Close', {
          duration: 7000,
          panelClass: result.failed > 0 ? ['error-snackbar'] : ['success-snackbar']
        });

        if (result.errors && result.errors.length > 0) {
          console.error('Import errors:', result.errors);
        }

        this.loadEntries();
        event.target.value = ''; // Reset file input
      },
      error: (error) => {
        this.snackBar.open(
          `✗ Import failed: ${error.message || 'Unknown error'}`,
          'Close',
          {
            duration: 7000,
            panelClass: ['error-snackbar']
          }
        );
        event.target.value = ''; // Reset file input
      }
    });
  }

  formatDate(dateString: string | undefined): string {
    if (!dateString) return 'Never';
    return new Date(dateString).toLocaleString();
  }

  isExpired(entry: BlocklistEntry): boolean {
    if (!entry.expiresAt) return false;
    return new Date(entry.expiresAt) < new Date();
  }

  getTypeIcon(type: BlocklistEntryType): string {
    switch (type) {
      case BlocklistEntryType.IP:
        return 'router';
      case BlocklistEntryType.CIDR:
        return 'hub';
      case BlocklistEntryType.DOMAIN:
        return 'language';
      default:
        return 'block';
    }
  }

  getTypeColor(type: BlocklistEntryType): string {
    switch (type) {
      case BlocklistEntryType.IP:
        return 'text-blue-600';
      case BlocklistEntryType.CIDR:
        return 'text-purple-600';
      case BlocklistEntryType.DOMAIN:
        return 'text-green-600';
      default:
        return 'text-gray-600';
    }
  }

  getPlaceholder(): string {
    const type = this.entryForm.get('type')?.value;
    switch (type) {
      case BlocklistEntryType.IP:
        return 'e.g., 192.168.1.1 or 2001:0db8:85a3::8a2e:0370:7334';
      case BlocklistEntryType.CIDR:
        return 'e.g., 192.168.1.0/24 or 10.0.0.0/8';
      case BlocklistEntryType.DOMAIN:
        return 'e.g., spam-domain.com or *.malicious-site.net';
      default:
        return 'Enter value';
    }
  }

  Math = Math;

  nextPage(): void {
    if ((this.currentPage + 1) * this.pageSize < this.totalEntries) {
      this.currentPage++;
      this.loadEntries();
    }
  }

  previousPage(): void {
    if (this.currentPage > 0) {
      this.currentPage--;
      this.loadEntries();
    }
  }
}

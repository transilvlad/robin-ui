import { signalStore, withState, withMethods, withComputed, patchState } from '@ngrx/signals';
import { computed, inject } from '@angular/core';
import { Domain, DomainHealth } from '../models/domain.models';
import { DomainService } from '../services/domain.service';
import { lastValueFrom } from 'rxjs';

interface DomainState {
  domains: Domain[];
  selectedDomain: Domain | null;
  healthMap: Record<number, DomainHealth[]>;
  loading: boolean;
  error: string | null;
  totalElements: number;
  currentPage: number;
}

const initialState: DomainState = {
  domains: [],
  selectedDomain: null,
  healthMap: {},
  loading: false,
  error: null,
  totalElements: 0,
  currentPage: 0,
};

export const DomainStore = signalStore(
  { providedIn: 'root' },
  withState(initialState),
  withComputed((store) => ({
    domainCount: computed(() => store.domains().length),
    activeCount: computed(() => store.domains().filter(d => d.status === 'ACTIVE').length),
    errorCount: computed(() => store.domains().filter(d => d.status === 'ERROR').length),
  })),
  withMethods((store, domainService = inject(DomainService)) => ({
    async loadDomains(page = 0) {
      patchState(store, { loading: true, error: null });
      const result = await lastValueFrom(domainService.getDomains(page));
      if (result.ok) {
        patchState(store, {
          domains: result.value.content,
          totalElements: result.value.totalElements,
          currentPage: page,
          loading: false,
        });
      } else {
        patchState(store, { error: 'Failed to load domains', loading: false });
      }
    },
    selectDomain(domain: Domain | null) {
      patchState(store, { selectedDomain: domain });
    },
    updateHealth(domainId: number, health: DomainHealth[]) {
      patchState(store, { healthMap: { ...store.healthMap(), [domainId]: health } });
    },
  }))
);

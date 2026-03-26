import { Injectable, computed, signal } from '@angular/core';

export type UserRole = 'ADMIN' | 'READ_ONLY';

@Injectable({ providedIn: 'root' })
export class RbacService {
  private readonly roleState = signal<UserRole>('ADMIN'); // Defaulting to ADMIN for demo

  readonly currentRole = computed(() => this.roleState());
  readonly isAdmin = computed(() => this.roleState() === 'ADMIN');
  readonly isReadOnly = computed(() => this.roleState() === 'READ_ONLY');

  setRole(role: UserRole) {
    this.roleState.set(role);
  }
}

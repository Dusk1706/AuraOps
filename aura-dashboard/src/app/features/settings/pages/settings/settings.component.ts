import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RbacService, UserRole } from '../../../../core/services/rbac.service';
import { MatCardModule } from '@angular/material/card';
import { MatRadioModule } from '@angular/material/radio';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatIconModule } from '@angular/material/icon';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [CommonModule, MatCardModule, MatRadioModule, MatSlideToggleModule, MatIconModule, FormsModule],
  template: `
    <div class="settings-container">
      <div class="header">
        <h1>Command Center Settings</h1>
      </div>

      <div class="content">
        <mat-card class="rbac-card">
          <mat-card-header>
            <mat-card-title><mat-icon>security</mat-icon> RBAC & Security</mat-card-title>
          </mat-card-header>
          <mat-card-content>
            <p>Select role to simulate UI behavior:</p>
            <mat-radio-group [(ngModel)]="currentRole" (change)="onRoleChange($event.value)">
              <mat-radio-button value="ADMIN">Administrator</mat-radio-button>
              <mat-radio-button value="READ_ONLY">Read Only</mat-radio-button>
            </mat-radio-group>
          </mat-card-content>
        </mat-card>

        <mat-card class="integration-card">
          <mat-card-header>
            <mat-card-title><mat-icon>device_hub</mat-icon> Integrations Status</mat-card-title>
          </mat-card-header>
          <mat-card-content>
            <div class="status-item">
              <span>Vault Integration</span>
              <mat-slide-toggle [checked]="true" [disabled]="rbac.isReadOnly()"></mat-slide-toggle>
            </div>
            <div class="status-item">
              <span>Istio Mesh Control</span>
              <mat-slide-toggle [checked]="true" [disabled]="rbac.isReadOnly()"></mat-slide-toggle>
            </div>
            <div class="status-item">
              <span>Healer Policy Engine</span>
              <mat-slide-toggle [checked]="true" [disabled]="rbac.isReadOnly()"></mat-slide-toggle>
            </div>
          </mat-card-content>
        </mat-card>
      </div>
    </div>
  `,
  styles: [`
    .settings-container {
      padding: 24px;
      color: white;
    }

    .header h1 {
      margin: 0 0 24px 0;
      font-size: 24px;
      font-weight: 300;
      color: white;
    }

    .content {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 24px;
    }

    mat-card {
      background: #1e1e2d;
      color: white;
    }

    mat-card-title {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    mat-radio-group {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }

    .status-item {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 12px 0;
      border-bottom: 1px solid #334155;
    }

    .status-item:last-child {
      border-bottom: none;
    }
  `]
})
export class SettingsComponent {
  rbac = inject(RbacService);
  currentRole = this.rbac.currentRole();

  onRoleChange(newRole: UserRole) {
    this.rbac.setRole(newRole);
  }
}

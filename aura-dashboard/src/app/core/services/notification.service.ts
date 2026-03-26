import { Injectable, signal } from '@angular/core';

export interface Notification {
  message: string;
  type: 'success' | 'error' | 'info';
  timestamp: Date;
}

@Injectable({ providedIn: 'root' })
export class NotificationService {
  readonly notifications = signal<Notification[]>([]);

  show(message: string, type: 'success' | 'error' | 'info' = 'info'): void {
    const notification: Notification = { message, type, timestamp: new Date() };
    this.notifications.update((current) => [notification, ...current].slice(0, 5));
    
    // Auto-remove after 5 seconds
    setTimeout(() => {
      this.notifications.update((current) => current.filter((n) => n !== notification));
    }, 5000);
  }

  success(message: string): void {
    this.show(message, 'success');
  }

  error(message: string): void {
    this.show(message, 'error');
  }

  info(message: string): void {
    this.show(message, 'info');
  }
}

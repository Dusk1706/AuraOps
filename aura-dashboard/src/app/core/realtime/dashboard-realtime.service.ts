import { isPlatformBrowser } from '@angular/common';
import { DestroyRef, Injectable, PLATFORM_ID, inject } from '@angular/core';
import { Client, IMessage } from '@stomp/stompjs';
import {
  DASHBOARD_REALTIME_CONFIG,
  DashboardRealtimeConfig,
} from '../config/dashboard-realtime.config';
import { HealerEventWire } from '../models/healer-event.model';
import { mapWireEvent } from './event-mapper';
import { DashboardStoreService } from './dashboard-store.service';

@Injectable({ providedIn: 'root' })
export class DashboardRealtimeService {
  private readonly destroyRef = inject(DestroyRef);
  private readonly platformId = inject(PLATFORM_ID);
  private readonly config = inject<DashboardRealtimeConfig>(DASHBOARD_REALTIME_CONFIG);
  private readonly store = inject(DashboardStoreService);

  private client?: Client;
  private started = false;

  start(): void {
    if (this.started) {
      return;
    }

    this.started = true;
    if (!isPlatformBrowser(this.platformId)) {
      this.store.setConnectionLabel('DISCONNECTED');
      return;
    }

    this.startStompStream();
  }

  reconnect(): void {
    this.disposeConnections();
    this.started = false;
    this.start();
  }

  private startStompStream(): void {
    this.store.setConnectionLabel('CONNECTING');
    const brokerURL = this.toWebSocketUrl(this.config.wsEndpoint);

    const client = new Client({
      reconnectDelay: this.config.reconnectDelayMs,
      brokerURL,
      onConnect: () => {
        this.store.setConnectionLabel('CONNECTED');
        client.subscribe(this.config.topic, (message) => this.onMessage(message));
      },
      onStompError: () => {
        this.store.setConnectionLabel('DISCONNECTED');
      },
      onWebSocketClose: () => {
        this.store.setConnectionLabel('DISCONNECTED');
      },
      onWebSocketError: () => {
        this.store.setConnectionLabel('DISCONNECTED');
      },
    });

    client.activate();
    this.client = client;

    this.destroyRef.onDestroy(() => {
      this.disposeConnections();
    });
  }

  private toWebSocketUrl(endpoint: string): string {
    if (endpoint.startsWith('ws://') || endpoint.startsWith('wss://')) {
      return endpoint;
    }

    if (endpoint.startsWith('http://')) {
      return `ws://${endpoint.slice('http://'.length)}`;
    }

    if (endpoint.startsWith('https://')) {
      return `wss://${endpoint.slice('https://'.length)}`;
    }

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const normalizedEndpoint = endpoint.startsWith('/') ? endpoint : `/${endpoint}`;
    return `${protocol}//${window.location.host}${normalizedEndpoint}`;
  }

  private onMessage(message: IMessage): void {
    const payload = this.parsePayload(message.body);
    if (!payload) {
      return;
    }
    try {
      this.store.ingestEvent(mapWireEvent(payload));
    } catch (error) {
      console.error('Received invalid healer event payload', error, payload);
    }
  }

  private parsePayload(rawBody: string): HealerEventWire | null {
    try {
      const parsed = JSON.parse(rawBody) as Partial<HealerEventWire>;
      if (
        typeof parsed.event_type !== 'string' ||
        typeof parsed.policy !== 'string' ||
        typeof parsed.action !== 'string' ||
        typeof parsed.severity !== 'string' ||
        typeof parsed.timestamp !== 'string' ||
        typeof parsed.service_name !== 'string' ||
        parsed.service_name.trim().length === 0 ||
        typeof parsed.namespace !== 'string' ||
        parsed.namespace.trim().length === 0
      ) {
        console.error('Received malformed healer event payload', parsed);
        return null;
      }

      return parsed as HealerEventWire;
    } catch (error) {
      console.error('Failed to parse healer event payload', error, rawBody);
      return null;
    }
  }

  private disposeConnections(): void {
    this.client?.deactivate();
    this.client = undefined;
  }
}

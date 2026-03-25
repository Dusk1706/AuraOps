import { TestBed } from '@angular/core/testing';
import { DashboardStoreService } from './dashboard-store.service';
import { HealerEvent } from '../models/healer-event.model';

describe('DashboardStoreService', () => {
  let service: DashboardStoreService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(DashboardStoreService);
  });

  it('should prepend incoming events', () => {
    const baselineEventsCount = service.events().length;

    const event: HealerEvent = {
      eventType: 'RECONCILIATION_STARTED',
      policy: 'checkout-service-heal',
      action: 'RESTART',
      severity: 'HIGH',
      timestamp: new Date().toISOString(),
      serviceName: 'checkout-service',
      namespace: 'prod',
    };

    service.ingestEvent(event);

    expect(service.events()[0]).toEqual(event);
    expect(service.events().length).toBe(baselineEventsCount + 1);
  });

  it('should mark node as healthy after successful reconciliation', () => {
    const event: HealerEvent = {
      eventType: 'RECONCILIATION_COMPLETED',
      policy: 'payment-service-heal',
      action: 'RESTART',
      severity: 'HIGH',
      timestamp: new Date().toISOString(),
      serviceName: 'payment-service',
      namespace: 'prod',
    };

    service.ingestEvent(event);

    const node = service
      .nodes()
      .find((item) => item.serviceName === 'payment-service' && item.namespace === 'prod');

    expect(node?.health).toBe('HEALTHY');
  });
});

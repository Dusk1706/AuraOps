import { TestBed } from '@angular/core/testing';
import { DashboardStoreService } from './dashboard-store.service';
import { HealerEvent } from '../models/healer-event.model';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';

describe('DashboardStoreService', () => {
  let service: DashboardStoreService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [DashboardStoreService]
    });
    service = TestBed.inject(DashboardStoreService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  it('should initialize state from backend', async () => {
    const initPromise = service.init();

    const nodesRequest = httpMock.expectOne('/api/dashboard/nodes');
    nodesRequest.flush([{ serviceName: 'test-service', namespace: 'default', health: 'HEALTHY' }]);

    const eventsRequest = httpMock.expectOne('/api/dashboard/events');
    eventsRequest.flush([]);

    await initPromise;

    expect(service.nodes().length).toBe(1);
    expect(service.nodes()[0].serviceName).toBe('test-service');
  });

  it('should prepend incoming events', () => {
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
    expect(service.events().length).toBe(1);
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

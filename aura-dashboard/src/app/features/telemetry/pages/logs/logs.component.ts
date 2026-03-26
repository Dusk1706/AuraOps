import { Component, ElementRef, OnDestroy, OnInit, PLATFORM_ID, ViewChild, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { DashboardRealtimeService } from '../../../../core/realtime/dashboard-realtime.service';
import { Subscription, interval } from 'rxjs';
import { FormsModule } from '@angular/forms';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { isPlatformBrowser } from '@angular/common';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';

interface LogEntry {
  id: number;
  timestamp: Date;
  severity: 'INFO' | 'WARN' | 'ERROR' | 'DEBUG';
  namespace: string;
  pod: string;
  message: string;
  jsonPayload?: any;
  expanded?: boolean;
}

@Component({
  selector: 'app-logs',
  standalone: true,
  imports: [
    CommonModule, 
    FormsModule, 
    MatSelectModule, 
    MatSlideToggleModule, 
    MatButtonModule, 
    MatIconModule,
    MatInputModule,
    MatFormFieldModule
  ],
  template: `
    <div class="logs-container">
      <div class="toolbar">
        <h2>Live Telemetry: Engine Room</h2>
        <div class="filters">
          <mat-form-field appearance="outline" class="filter-field">
            <mat-label>Severity</mat-label>
            <mat-select [(ngModel)]="filterSeverity" (selectionChange)="applyFilters()">
              <mat-option value="ALL">All Levels</mat-option>
              <mat-option value="ERROR">Errors</mat-option>
              <mat-option value="WARN">Warnings</mat-option>
              <mat-option value="INFO">Info</mat-option>
            </mat-select>
          </mat-form-field>
          
          <mat-form-field appearance="outline" class="filter-field">
            <mat-label>Namespace</mat-label>
            <input matInput [(ngModel)]="filterNamespace" (keyup)="applyFilters()" placeholder="e.g. core-services">
          </mat-form-field>

          <mat-form-field appearance="outline" class="filter-field">
            <mat-label>Pod</mat-label>
            <input matInput [(ngModel)]="filterPod" (keyup)="applyFilters()" placeholder="e.g. auth-pod-1">
          </mat-form-field>

          <mat-slide-toggle [(ngModel)]="liveTail" color="primary">Live Tail</mat-slide-toggle>
          <button mat-icon-button (click)="clearLogs()" title="Clear Logs"><mat-icon>delete_sweep</mat-icon></button>
        </div>
      </div>

      <div class="console-wrapper">
        <div class="console" #consoleOutput>
          @for (log of filteredLogs(); track log.id) {
            <div class="log-line" [ngClass]="log.severity.toLowerCase()">
              <span class="timestamp">[{{ log.timestamp | date:'yyyy-MM-dd HH:mm:ss.SSS' }}]</span>
              <span class="severity">[{{ log.severity }}]</span>
              <span class="source">{{ log.namespace }}/{{ log.pod }}</span>
              <span class="message">{{ log.message }}</span>
              
              @if (log.jsonPayload) {
                <button class="expand-btn" (click)="log.expanded = !log.expanded">
                  {{ log.expanded ? '[-] Collapse' : '[+] Expand JSON' }}
                </button>
              }
            </div>
            
            @if (log.jsonPayload && log.expanded) {
              <pre class="json-block" [innerHTML]="highlightJson(log.jsonPayload)"></pre>
            }
          }
        </div>
      </div>
    </div>
  `,
  styles: [`
    .logs-container {
      display: flex;
      flex-direction: column;
      height: 100%;
      padding: 16px;
      gap: 16px;
      color: white;
    }

    .toolbar {
      display: flex;
      justify-content: space-between;
      align-items: center;
      background: #1e1e2d;
      padding: 12px 24px;
      border-radius: 8px;
    }

    .toolbar h2 {
      margin: 0;
      font-size: 20px;
      font-weight: 400;
    }

    .filters {
      display: flex;
      gap: 16px;
      align-items: center;
    }

    .filter-field {
      width: 150px;
    }

    .filter-field.wide {
      width: 200px;
    }

    ::ng-deep .mat-mdc-form-field-subscript-wrapper {
      display: none;
    }

    .console-wrapper {
      flex: 1;
      background: #0d1117;
      border-radius: 8px;
      border: 1px solid #30363d;
      overflow: hidden;
      display: flex;
      flex-direction: column;
    }

    .console {
      flex: 1;
      overflow-y: auto;
      padding: 12px;
      font-family: 'Fira Code', 'Courier New', Courier, monospace;
      font-size: 13px;
    }

    .log-line {
      display: flex;
      gap: 12px;
      padding: 4px 0;
      word-break: break-all;
    }

    .log-line:hover {
      background: rgba(255, 255, 255, 0.05);
    }

    .timestamp { color: #8b949e; }
    .source { color: #d2a8ff; }
    
    .info .severity { color: #58a6ff; }
    .info .message { color: #c9d1d9; }
    
    .warn .severity { color: #d29922; }
    .warn .message { color: #e3b341; }
    
    .error .severity { color: #f85149; }
    .error .message { color: #ff7b72; font-weight: bold; }

    .expand-btn {
      background: none;
      border: none;
      color: #58a6ff;
      cursor: pointer;
      font-family: inherit;
      font-size: inherit;
      margin-left: auto;
    }

    .expand-btn:hover {
      text-decoration: underline;
    }

    .json-block {
      background: #161b22;
      border: 1px solid #30363d;
      border-left: 4px solid #58a6ff;
      padding: 8px;
      margin: 4px 0 12px 32px;
      overflow-x: auto;
    }

    .json-block :is(.json-key) {
      color: #7dd3fc;
    }

    .json-block :is(.json-string) {
      color: #86efac;
    }

    .json-block :is(.json-number) {
      color: #f9a8d4;
    }

    .json-block :is(.json-boolean) {
      color: #fcd34d;
    }

    .json-block :is(.json-null) {
      color: #94a3b8;
      font-style: italic;
    }

    @media (max-width: 1200px) {
      .toolbar {
        align-items: flex-start;
        flex-direction: column;
        gap: 12px;
      }

      .filters {
        flex-wrap: wrap;
      }
    }
  `]
})
export class LogsComponent implements OnInit, OnDestroy {
  @ViewChild('consoleOutput') private consoleOutput?: ElementRef<HTMLElement>;

  private realtime = inject(DashboardRealtimeService);
  private sanitizer = inject(DomSanitizer);
  private platformId = inject(PLATFORM_ID);
  private sub?: Subscription;
  private autoMockSub?: Subscription;

  allLogs = signal<LogEntry[]>([]);
  filteredLogs = signal<LogEntry[]>([]);

  filterSeverity = 'ALL';
  filterNamespace = '';
  filterPod = '';
  liveTail = true;

  private mockId = 0;

  ngOnInit() {
    this.sub = this.realtime.rawLogs$.subscribe((log: any) => {
      this.handleIncomingLog(log);
    });

    if (isPlatformBrowser(this.platformId)) {
      // Mock incoming logs for demonstration
      this.autoMockSub = interval(2000).subscribe(() => this.generateMockLog());
    }
  }

  ngOnDestroy() {
    this.sub?.unsubscribe();
    this.autoMockSub?.unsubscribe();
  }

  handleIncomingLog(log: any) {
    if (!log.id) {
       // Convert to LogEntry roughly if it's from system
       log.id = this.mockId++;
       log.timestamp = new Date();
       log.severity = log.severity || 'INFO';
    }
    
    this.allLogs.update((logs: LogEntry[]) => {
      const newLogs = [...logs, log];
      if (newLogs.length > 1000) newLogs.shift(); // Keep max 1000
      return newLogs;
    });

    this.applyFilters();
  }

  applyFilters() {
    const logs = this.allLogs();
    const sf = this.filterSeverity;
    const nf = this.filterNamespace.toLowerCase();
    const pf = this.filterPod.toLowerCase();

    const result = logs.filter((log: LogEntry) => {
      if (sf !== 'ALL' && log.severity !== sf) return false;
      if (nf && !log.namespace.toLowerCase().includes(nf)) return false;
      if (pf && !log.pod.toLowerCase().includes(pf)) return false;
      return true;
    });

    this.filteredLogs.set(result);
  }

  clearLogs() {
    this.allLogs.set([]);
    this.filteredLogs.set([]);
  }

  highlightJson(payload: unknown): SafeHtml {
    const escapedJson = JSON.stringify(payload, null, 2)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;');

    const highlighted = escapedJson.replace(
      /(\"(?:\\u[\da-fA-F]{4}|\\[^u]|[^\\\"])*\"\s*:?)|(\btrue\b|\bfalse\b)|(\bnull\b)|(-?\d+(?:\.\d+)?(?:[eE][+\-]?\d+)?)/g,
      (match, stringToken, booleanToken, nullToken, numberToken) => {
        if (stringToken) {
          const className = stringToken.endsWith(':') ? 'json-key' : 'json-string';
          return `<span class="${className}">${stringToken}</span>`;
        }

        if (booleanToken) {
          return `<span class="json-boolean">${booleanToken}</span>`;
        }

        if (nullToken) {
          return `<span class="json-null">${nullToken}</span>`;
        }

        if (numberToken) {
          return `<span class="json-number">${numberToken}</span>`;
        }

        return match;
      }
    );

    return this.sanitizer.bypassSecurityTrustHtml(highlighted);
  }

  private generateMockLog() {
    const severities: ('INFO' | 'WARN' | 'ERROR')[] = ['INFO', 'INFO', 'INFO', 'WARN', 'ERROR'];
    const sev = severities[Math.floor(Math.random() * severities.length)];
    const ns = ['core-services', 'payments', 'auth-system', 'data-pipeline'][Math.floor(Math.random() * 4)];
    
    const log: LogEntry = {
      id: this.mockId++,
      timestamp: new Date(),
      severity: sev,
      namespace: ns,
      pod: ns + '-pod-' + Math.floor(Math.random() * 1000),
      message: sev === 'ERROR' ? 'Connection refused to upstream service' : (sev === 'WARN' ? 'High latency detected' : 'Request processed successfully'),
      jsonPayload: Math.random() > 0.7 ? { traceId: '12345', duration: 124, status: 200 } : undefined
    };

    this.handleIncomingLog(log);

    if (this.liveTail && isPlatformBrowser(this.platformId)) {
      setTimeout(() => {
        const consoleEl = this.consoleOutput?.nativeElement;
        if (consoleEl) {
          consoleEl.scrollTop = consoleEl.scrollHeight;
        }
      }, 50);
    }
  }
}

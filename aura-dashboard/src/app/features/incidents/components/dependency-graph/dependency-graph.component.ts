import { Component, Input, computed } from '@angular/core';
import { CommonModule } from '@angular/common';

interface MockNode {
  id: string;
  x: number;
  y: number;
  label: string;
  isTarget: boolean;
}

interface MockLink {
  source: string;
  target: string;
}

@Component({
  selector: 'app-dependency-graph',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="graph-container">
      <svg width="100%" height="300" viewBox="0 0 600 300">
        <defs>
          <marker id="arrow" viewBox="0 0 10 10" refX="24" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
            <path d="M 0 0 L 10 5 L 0 10 z" fill="#64748b" />
          </marker>
        </defs>

        @for (link of svgLinks(); track link.path) {
          <path [attr.d]="link.path" fill="none" class="link-path" marker-end="url(#arrow)"></path>
        }

        @for (node of nodes; track node.id) {
          <g [attr.transform]="'translate(' + node.x + ',' + node.y + ')'" class="node">
            <circle r="20" [class.target]="node.isTarget"></circle>
            <text y="35" text-anchor="middle" class="node-label">{{ node.label }}</text>
          </g>
        }
      </svg>
    </div>
  `,
  styles: [`
    .graph-container {
      width: 100%;
      height: 300px;
      background: #1e1e2d;
      border-radius: 8px;
      border: 1px solid #334155;
    }
    .node circle {
      fill: #334155;
      stroke: #475569;
      stroke-width: 2;
    }
    .node circle.target {
      fill: #ef4444;
      stroke: #7f1d1d;
      animation: pulse 2s infinite;
    }
    .node-label {
      fill: #e2e8f0;
      font-size: 12px;
    }
    .link-path {
      stroke: #64748b;
      stroke-width: 2;
      stroke-dasharray: 4;
      animation: dash 20s linear infinite;
    }

    @keyframes pulse {
      0% {
        box-shadow: 0 0 0 0 rgba(239, 68, 68, 0.7);
      }
      70% {
        box-shadow: 0 0 0 10px rgba(239, 68, 68, 0);
      }
      100% {
        box-shadow: 0 0 0 0 rgba(239, 68, 68, 0);
      }
    }

    @keyframes dash {
      to {
        stroke-dashoffset: -100;
      }
    }
  `]
})
export class DependencyGraphComponent {
  @Input() targetService: string = '';

  // Mock a graph centered roughly globally
  nodes: MockNode[] = [
    { id: 'gw', x: 100, y: 150, label: 'API Gateway', isTarget: false },
    { id: 'auth', x: 300, y: 50, label: 'Auth Service', isTarget: false },
    { id: 'target', x: 300, y: 150, label: 'Current Service', isTarget: true },
    { id: 'db', x: 500, y: 150, label: 'Database', isTarget: false },
    { id: 'cache', x: 300, y: 250, label: 'Redis Cache', isTarget: false },
  ];

  links: MockLink[] = [
    { source: 'gw', target: 'auth' },
    { source: 'gw', target: 'target' },
    { source: 'target', target: 'db' },
    { source: 'target', target: 'cache' },
  ];

  svgLinks = computed(() => {
    return this.links.map(link => {
      const source = this.nodes.find(n => n.id === link.source);
      const target = this.nodes.find(n => n.id === link.target);
      if (!source || !target) return { path: '' };

      const dx = target.x - source.x;
      const dy = target.y - source.y;
      const dr = Math.sqrt(dx * dx + dy * dy);
      
      // Arc path
      const path = 'M' + source.x + ',' + source.y + ' Q' + (source.x + dx/2) + ',' + (source.y + dy/2 - 20) + ' ' + target.x + ',' + target.y;
      return { path };
    });
  });

  ngOnChanges() {
    // dynamically update the target service label if needed
    if (this.targetService) {
      const tgt = this.nodes.find(n => n.id === 'target');
      if (tgt) tgt.label = this.targetService;
    }
  }
}

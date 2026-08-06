import {
  afterRenderEffect,
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  inject,
  input,
  Renderer2,
  viewChild,
} from '@angular/core';
import {
  Activity,
  ArrowLeft,
  BookOpen,
  Check,
  ChevronLeft,
  ChevronRight,
  ChevronDown,
  CircleAlert,
  Ellipsis,
  Download,
  FileText,
  House,
  Languages,
  LibraryBig,
  LoaderCircle,
  List,
  Menu,
  Moon,
  Plus,
  Search,
  ShieldCheck,
  Settings,
  Sparkles,
  Sun,
  Trash2,
  Type,
  Upload,
  User,
  Video,
  X,
  type IconNode,
} from 'lucide';

export const loquelaIcons = {
  activity: Activity,
  'arrow-left': ArrowLeft,
  book: BookOpen,
  check: Check,
  'chevron-left': ChevronLeft,
  'chevron-right': ChevronRight,
  'chevron-down': ChevronDown,
  danger: CircleAlert,
  download: Download,
  document: FileText,
  home: House,
  languages: Languages,
  library: LibraryBig,
  loading: LoaderCircle,
  list: List,
  menu: Menu,
  more: Ellipsis,
  moon: Moon,
  plus: Plus,
  search: Search,
  shield: ShieldCheck,
  settings: Settings,
  sparkles: Sparkles,
  sun: Sun,
  trash: Trash2,
  type: Type,
  upload: Upload,
  user: User,
  video: Video,
  x: X,
} as const satisfies Record<string, IconNode>;

export type AppIconName = keyof typeof loquelaIcons;

@Component({
  selector: 'app-icon',
  template: `
    <svg
      #svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-linecap="round"
      stroke-linejoin="round"
      [attr.stroke-width]="strokeWidth()"
      [attr.width]="size()"
      [attr.height]="size()"
      [attr.aria-hidden]="decorative() ? 'true' : null"
      [attr.aria-label]="decorative() ? null : label()"
      [attr.role]="decorative() ? null : 'img'"
    ></svg>
  `,
  styles: `
    :host {
      display: inline-flex;
      flex: 0 0 auto;
      line-height: 0;
    }

    svg {
      display: block;
    }

    :host(.app-icon--spin) svg {
      animation: app-icon-spin 0.8s linear infinite;
    }

    @keyframes app-icon-spin {
      to {
        transform: rotate(1turn);
      }
    }

    @media (prefers-reduced-motion: reduce) {
      :host(.app-icon--spin) svg {
        animation-duration: 1.8s;
      }
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppIcon {
  private readonly renderer = inject(Renderer2);
  private readonly svg = viewChild.required<ElementRef<SVGSVGElement>>('svg');

  readonly name = input.required<AppIconName>();
  readonly size = input(20);
  readonly strokeWidth = input(2);
  readonly label = input<string>();

  readonly decorative = computed(() => !this.label());

  constructor() {
    afterRenderEffect(() => this.renderIcon(loquelaIcons[this.name()]));
  }

  private renderIcon(nodes: IconNode): void {
    const svg = this.svg().nativeElement;
    while (svg.firstChild) {
      this.renderer.removeChild(svg, svg.firstChild);
    }

    for (const [tag, attributes] of nodes) {
      const child = this.renderer.createElement(tag, 'svg') as SVGElement;
      for (const [name, value] of Object.entries(attributes)) {
        if (value !== undefined) {
          this.renderer.setAttribute(child, name, String(value));
        }
      }
      this.renderer.appendChild(svg, child);
    }
  }
}

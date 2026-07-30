import {
  ChangeDetectionStrategy,
  Component,
  effect,
  ElementRef,
  inject,
  input,
  Renderer2,
  viewChild,
} from '@angular/core';

/**
 * Reviewed resource-URL boundary for practice video. The backend supplies only
 * a validated YouTube id and non-negative start second; arbitrary URLs never
 * reach this component or the iframe.
 */
@Component({
  selector: 'app-youtube-practice-player',
  template: '<div #playerHost class="player-host"></div>',
  styleUrl: './youtube-practice-player.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class YoutubePracticePlayer {
  private readonly renderer = inject(Renderer2);
  private readonly playerHost = viewChild<ElementRef<HTMLElement>>('playerHost');

  readonly videoId = input.required<string>();
  readonly startSeconds = input.required<number>();
  readonly timestampLabel = input('');

  constructor() {
    effect(() => {
      const host = this.playerHost()?.nativeElement;
      const videoId = this.videoId();
      const startSeconds = this.startSeconds();
      const timestamp = this.timestampLabel();
      if (!host) return;

      while (host.firstChild) {
        this.renderer.removeChild(host, host.firstChild);
      }
      if (!/^[A-Za-z0-9_-]{11}$/u.test(videoId) || !Number.isSafeInteger(startSeconds)) return;
      if (startSeconds < 0) return;

      const iframe = this.renderer.createElement('iframe') as HTMLIFrameElement;
      this.renderer.addClass(iframe, 'practice-video');
      this.renderer.setAttribute(
        iframe,
        'src',
        `https://www.youtube-nocookie.com/embed/${videoId}?start=${startSeconds}&rel=0`,
      );
      this.renderer.setAttribute(
        iframe,
        'title',
        timestamp ? `YouTube context at ${timestamp}` : 'YouTube source context',
      );
      this.renderer.setAttribute(iframe, 'loading', 'lazy');
      this.renderer.setAttribute(iframe, 'referrerpolicy', 'strict-origin-when-cross-origin');
      this.renderer.setAttribute(
        iframe,
        'allow',
        'accelerometer; autoplay; encrypted-media; gyroscope; picture-in-picture; web-share',
      );
      this.renderer.setAttribute(iframe, 'allowfullscreen', '');
      this.renderer.appendChild(host, iframe);
    });
  }
}

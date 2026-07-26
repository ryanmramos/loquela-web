import { ErrorHandler, inject, Injectable } from '@angular/core';
import { Router } from '@angular/router';

@Injectable()
export class AppErrorHandler implements ErrorHandler {
  private readonly router = inject(Router);

  handleError(): void {
    // Never serialize an unknown exception: it may contain request data or credentials.
    console.error('An unexpected application error occurred.');

    if (this.router.url !== '/error') {
      void this.router.navigateByUrl('/error');
    }
  }
}

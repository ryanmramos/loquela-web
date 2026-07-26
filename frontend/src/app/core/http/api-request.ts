import { HttpRequest } from '@angular/common/http';

import { RuntimeConfig } from '@core/config/runtime-config';

export function isApiRequest(request: HttpRequest<unknown>, config: RuntimeConfig): boolean {
  return request.url === config.apiBasePath || request.url.startsWith(`${config.apiBasePath}/`);
}

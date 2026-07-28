import { readFile } from 'node:fs/promises';

export async function readBrowserOpenApi(contractPath) {
  const source = await readFile(contractPath, 'utf8');
  return JSON.parse(source);
}

export function validateBrowserOpenApi(contract) {
  const errors = [];

  if (!isRecord(contract) || !String(contract.openapi ?? '').startsWith('3.1.')) {
    errors.push('The browser contract must use OpenAPI 3.1.');
    return errors;
  }

  if (contract.servers?.length !== 1 || contract.servers[0]?.url !== '/') {
    errors.push('The generated browser contract must declare only the same-origin "/" server.');
  }

  const sessionGet = contract.paths?.['/api/v1/session']?.get;
  if (!isRecord(sessionGet)) {
    errors.push('GET /api/v1/session is required.');
  } else {
    if (sessionGet.operationId !== 'getBrowserSession') {
      errors.push('GET /api/v1/session must keep operationId "getBrowserSession".');
    }
    if (Array.isArray(sessionGet.parameters) && sessionGet.parameters.length > 0) {
      errors.push('GET /api/v1/session must not expose framework parameters.');
    }
    requireSchemaReference(
      sessionGet,
      '200',
      'application/json',
      '#/components/schemas/BrowserSessionDto',
      errors,
    );
    requireSchemaReference(
      sessionGet,
      '401',
      'application/problem+json',
      '#/components/schemas/ApiProblemDto',
      errors,
    );
  }

  const sessionLogout = contract.paths?.['/api/v1/session/logout']?.post;
  if (!isRecord(sessionLogout) || sessionLogout.operationId !== 'logoutBrowserSession') {
    errors.push('POST /api/v1/session/logout must keep operationId "logoutBrowserSession".');
  } else if (!isRecord(sessionLogout.responses?.['204'])) {
    errors.push('POST /api/v1/session/logout must declare a 204 response.');
  }

  const googleLogin = contract.paths?.['/api/v1/auth/google']?.get;
  if (!isRecord(googleLogin) || googleLogin.operationId !== 'startGoogleLogin') {
    errors.push('GET /api/v1/auth/google must keep operationId "startGoogleLogin".');
  } else if (!isRecord(googleLogin.responses?.['302'])) {
    errors.push('GET /api/v1/auth/google must declare its 302 redirect.');
  }

  const dashboardGet = contract.paths?.['/api/v1/dashboard']?.get;
  if (!isRecord(dashboardGet) || dashboardGet.operationId !== 'getDashboard') {
    errors.push('GET /api/v1/dashboard must keep operationId "getDashboard".');
  } else {
    requireSchemaReference(
      dashboardGet,
      '200',
      'application/json',
      '#/components/schemas/DashboardDto',
      errors,
    );
  }

  const pastedTextCreate = contract.paths?.['/api/v1/sources']?.post;
  if (!isRecord(pastedTextCreate) || pastedTextCreate.operationId !== 'createPastedTextSource') {
    errors.push('POST /api/v1/sources must keep operationId "createPastedTextSource".');
  } else {
    const requestReference =
      pastedTextCreate.requestBody?.content?.['application/json']?.schema?.['$ref'];
    if (requestReference !== '#/components/schemas/CreatePastedTextSourceRequestDto') {
      errors.push('POST /api/v1/sources must accept CreatePastedTextSourceRequestDto.');
    }
    requireSchemaReference(
      pastedTextCreate,
      '202',
      'application/json',
      '#/components/schemas/PastedTextSourceDto',
      errors,
    );
  }

  const sourceList = contract.paths?.['/api/v1/sources']?.get;
  if (!isRecord(sourceList) || sourceList.operationId !== 'listSources') {
    errors.push('GET /api/v1/sources must keep operationId "listSources".');
  } else {
    requireSchemaReference(
      sourceList,
      '200',
      'application/json',
      '#/components/schemas/SourceListDto',
      errors,
    );
  }

  const sourceDetails = contract.paths?.['/api/v1/sources/{sourceReference}'];
  for (const [method, operationId] of [
    ['get', 'getSource'],
    ['patch', 'renameSource'],
    ['delete', 'deleteSource'],
  ]) {
    if (!isRecord(sourceDetails?.[method]) || sourceDetails[method].operationId !== operationId) {
      errors.push(
        `${method.toUpperCase()} /api/v1/sources/{sourceReference} must keep operationId "${operationId}".`,
      );
    }
  }

  const retrySource = contract.paths?.['/api/v1/sources/{sourceReference}/retry']?.post;
  if (!isRecord(retrySource) || retrySource.operationId !== 'retrySource') {
    errors.push(
      'POST /api/v1/sources/{sourceReference}/retry must keep operationId "retrySource".',
    );
  }

  const youtubeCreate = contract.paths?.['/api/v1/sources/youtube']?.post;
  if (!isRecord(youtubeCreate) || youtubeCreate.operationId !== 'createYouTubeSource') {
    errors.push('POST /api/v1/sources/youtube must keep operationId "createYouTubeSource".');
  }

  const uploadCreate = contract.paths?.['/api/v1/source-uploads']?.post;
  const uploadComplete =
    contract.paths?.['/api/v1/source-uploads/{uploadReference}/complete']?.post;
  if (!isRecord(uploadCreate) || uploadCreate.operationId !== 'createSourceUpload') {
    errors.push('POST /api/v1/source-uploads must keep operationId "createSourceUpload".');
  }
  if (!isRecord(uploadComplete) || uploadComplete.operationId !== 'completeSourceUpload') {
    errors.push(
      'POST /api/v1/source-uploads/{uploadReference}/complete must keep operationId "completeSourceUpload".',
    );
  }

  const schemas = contract.components?.schemas;
  requireFields(
    schemas?.BrowserSessionDto,
    ['userId', 'sourceLanguage', 'definitionLanguage', 'accountState', 'linkedAccounts'],
    'BrowserSessionDto',
    errors,
  );
  requireFields(schemas?.LinkedAccountsDto, ['google', 'whatsapp'], 'LinkedAccountsDto', errors);
  requireFields(
    schemas?.ApiProblemDto,
    ['type', 'title', 'status', 'code', 'correlationId', 'fieldErrors'],
    'ApiProblemDto',
    errors,
  );
  requireFields(schemas?.FieldErrorDto, ['field', 'code', 'message'], 'FieldErrorDto', errors);
  requireFields(
    schemas?.DashboardDto,
    ['generatedAt', 'languagePair', 'cards', 'sources', 'jobs', 'activity'],
    'DashboardDto',
    errors,
  );
  requireFields(
    schemas?.CardSummary,
    ['status', 'cardsToStudy', 'totalCards', 'newCardsThisWeek'],
    'CardSummary',
    errors,
  );
  requireFields(
    schemas?.SourceSummary,
    ['status', 'totalSources', 'readySources', 'processingSources'],
    'SourceSummary',
    errors,
  );
  requireFields(schemas?.JobSummary, ['status', 'activeJobs'], 'JobSummary', errors);
  requireFields(schemas?.ActivitySummary, ['status', 'items'], 'ActivitySummary', errors);
  requireFields(
    schemas?.ActivityItem,
    ['kind', 'title', 'detail', 'status', 'occurredAt', 'target'],
    'ActivityItem',
    errors,
  );
  requireFields(
    schemas?.CreatePastedTextSourceRequestDto,
    ['text'],
    'CreatePastedTextSourceRequestDto',
    errors,
  );
  requireFields(
    schemas?.PastedTextSourceDto,
    ['sourceId', 'reference', 'status', 'characterCount'],
    'PastedTextSourceDto',
    errors,
  );
  requireFields(
    schemas?.SourceListDto,
    ['items', 'page', 'pageSize', 'totalItems', 'totalPages'],
    'SourceListDto',
    errors,
  );
  requireFields(
    schemas?.BrowserSourceSummaryDto,
    [
      'reference',
      'kind',
      'status',
      'displayName',
      'language',
      'retryable',
      'createdAt',
      'updatedAt',
      'version',
    ],
    'BrowserSourceSummaryDto',
    errors,
  );
  requireFields(
    schemas?.BrowserSourceDetailsDto,
    [
      'reference',
      'kind',
      'status',
      'displayName',
      'language',
      'origin',
      'retryable',
      'createdAt',
      'updatedAt',
      'version',
    ],
    'BrowserSourceDetailsDto',
    errors,
  );
  requireFields(
    schemas?.SourceUploadIntentDto,
    [
      'uploadReference',
      'uploadMode',
      'completionPath',
      'maxBytes',
      'acceptedMediaTypes',
      'expiresAt',
    ],
    'SourceUploadIntentDto',
    errors,
  );
  requireFields(
    schemas?.AcceptedSourceDto,
    ['reference', 'status', 'existing'],
    'AcceptedSourceDto',
    errors,
  );

  for (const schemaName of Object.keys(schemas ?? {})) {
    if (schemaName.endsWith('Entity')) {
      errors.push(`JPA schema "${schemaName}" must not appear in the browser contract.`);
    }
  }

  const sessionScheme = contract.components?.securitySchemes?.browserSession;
  if (
    sessionScheme?.type !== 'apiKey' ||
    sessionScheme?.in !== 'cookie' ||
    sessionScheme?.name !== 'LOQUELA_SESSION'
  ) {
    errors.push('browserSession must remain the LOQUELA_SESSION cookie security scheme.');
  }

  return errors;
}

function requireSchemaReference(operation, status, mediaType, expectedReference, errors) {
  const reference = operation.responses?.[status]?.content?.[mediaType]?.schema?.['$ref'];
  if (reference !== expectedReference) {
    errors.push(
      `${status} ${mediaType} must reference ${expectedReference}, received ${String(reference)}.`,
    );
  }
}

function requireFields(schema, fields, schemaName, errors) {
  if (!isRecord(schema)) {
    errors.push(`${schemaName} is required.`);
    return;
  }

  const required = new Set(schema.required ?? []);
  for (const field of fields) {
    if (!isRecord(schema.properties?.[field]) || !required.has(field)) {
      errors.push(`${schemaName}.${field} must be present and required.`);
    }
  }
}

function isRecord(value) {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

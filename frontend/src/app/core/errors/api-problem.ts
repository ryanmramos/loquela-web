export interface ApiFieldError {
  readonly field: string;
  readonly code: string;
  readonly message: string;
}

export interface ApiProblem {
  readonly type: string;
  readonly title: string;
  readonly status: number;
  readonly code: string;
  readonly detail?: string;
  readonly correlationId: string;
  readonly fieldErrors?: readonly ApiFieldError[];
}

export class ApiProblemError extends Error {
  constructor(readonly problem: ApiProblem) {
    super(problem.title);
    this.name = 'ApiProblemError';
  }
}

import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Pagination } from './pagination';

describe('Pagination', () => {
  let fixture: ComponentFixture<Pagination>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [Pagination] }).compileComponents();
    fixture = TestBed.createComponent(Pagination);
    fixture.componentRef.setInput('currentPage', 2);
    fixture.componentRef.setInput('totalPages', 4);
    fixture.detectChanges();
  });

  it('announces the current position and emits an allowed next page', () => {
    const onPageChange = vi.fn();
    fixture.componentInstance.pageChange.subscribe(onPageChange);

    const navigation = fixture.nativeElement as HTMLElement;
    expect(navigation.textContent).toContain('Page 2 of 4');
    navigation.querySelectorAll('button')[1]?.click();

    expect(onPageChange).toHaveBeenCalledWith(3);
  });

  it('disables navigation beyond the available range', () => {
    fixture.componentRef.setInput('currentPage', 4);
    fixture.detectChanges();

    const buttons = (fixture.nativeElement as HTMLElement).querySelectorAll('button');
    expect(buttons[1]?.disabled).toBe(true);
  });
});

import { Injectable, computed, signal } from '@angular/core';

const KEY = 'dimabug.favoritos.v1';

@Injectable({ providedIn: 'root' })
export class FavoritosService {
  private readonly idsSignal = signal<number[]>(this.leer());
  readonly ids = computed(() => this.idsSignal());

  tiene(id: number): boolean {
    return this.idsSignal().includes(id);
  }

  toggle(id: number): void {
    const actual = this.idsSignal();
    const next = actual.includes(id) ? actual.filter((item) => item !== id) : [...actual, id];
    this.idsSignal.set(next);
    localStorage.setItem(KEY, JSON.stringify(next));
  }

  private leer(): number[] {
    try {
      const raw = JSON.parse(localStorage.getItem(KEY) || '[]');
      return Array.isArray(raw) ? raw.filter((id) => Number.isInteger(id)) : [];
    } catch {
      return [];
    }
  }
}

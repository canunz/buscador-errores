import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { FavoritosService } from '../../core/services/favoritos.service';
import { FrecuenteItem, InicioDashboard, InicioService } from '../../core/services/inicio.service';
import { primerNombre } from '../../core/models/usuario.model';
import { LoadingModalComponent } from '../../shared/ui/loading-modal.component';
import { TendenciaLineaComponent } from './tendencia-linea.component';

@Component({
  selector: 'app-inicio',
  standalone: true,
  imports: [DatePipe, ReactiveFormsModule, TendenciaLineaComponent, LoadingModalComponent],
  templateUrl: './inicio.component.html',
  styleUrl: './inicio.component.css',
})
export class InicioComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);
  private readonly inicioApi = inject(InicioService);
  private readonly favoritosApi = inject(FavoritosService);

  readonly primerNombre = computed(() => primerNombre(this.auth.usuario()?.usuarioNombre));
  readonly isAdmin = this.auth.isAdmin;
  readonly isSoporte = this.auth.isSoporte;
  readonly isStaff = this.auth.isStaff;
  readonly rolVista = computed(() => {
    if (this.isAdmin()) {
      return 'admin';
    }
    if (this.isSoporte()) {
      return 'soporte';
    }
    return 'usuario';
  });
  readonly favoritosCount = computed(() => this.favoritosApi.items().length);

  dashboard: InicioDashboard | null = null;
  error = '';

  searchForm = this.fb.nonNullable.group({
    q: [''],
  });

  ngOnInit(): void {
    if (!this.isStaff()) {
      this.dashboard = {
        conocimientos: 0,
        procedimientos: 0,
        ultimaActualizacion: null,
        frecuentes: [],
        escala: null,
      };
      return;
    }
    this.inicioApi.dashboard().subscribe({
      next: (data) => (this.dashboard = data),
      error: () => {
        if (!this.dashboard) {
          this.error = 'No se pudo cargar el panel operativo.';
        }
      },
    });
  }

  buscar(): void {
    const q = this.searchForm.controls.q.value.trim();
    void this.router.navigate(['/conocimiento'], {
      queryParams: q ? { q } : {},
    });
  }

  ir(url: string): void {
    this.router.navigateByUrl(url);
  }

  irFrecuente(item: FrecuenteItem): void {
    if (item.tipo === 'HARDWARE') {
      this.router.navigateByUrl('/plataforma/hardware');
      return;
    }
    if (item.tipo === 'PROCEDIMIENTO') {
      this.router.navigate(['/procedimientos'], { queryParams: { q: item.titulo } });
      return;
    }
    this.router.navigate(['/conocimiento'], { queryParams: { q: item.titulo } });
  }
}

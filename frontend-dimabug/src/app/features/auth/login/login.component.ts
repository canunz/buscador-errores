import { Component, OnDestroy, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService, CodigoAccesoError, SolicitudCodigoAcceso } from '../../../core/services/auth.service';

type Paso = 'identificar' | 'codigo';

/**
 * 1) el usuario escribe su correo;
 * 2) Administrador, Soporte y Usuario reciben el mismo código de 6 dígitos.
 */
@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css',
})
export class LoginComponent implements OnDestroy {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  paso: Paso = 'identificar';
  loading = false;
  reenviando = false;
  error = '';
  aviso = '';
  identificador = '';

  /** Momento en que vence el código, el reenvío y un bloqueo por intentos fallidos. */
  private venceEn = 0;
  private reenvioDesde = 0;
  private bloqueoHasta = 0;
  private ahora = Date.now();
  private reloj: ReturnType<typeof setInterval> | null = null;

  form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
  });

  codigoForm = this.fb.nonNullable.group({
    codigo: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]],
  });

  get segundosParaVencer(): number {
    return Math.max(0, Math.ceil((this.venceEn - this.ahora) / 1000));
  }

  get segundosParaReenviar(): number {
    return Math.max(0, Math.ceil((this.reenvioDesde - this.ahora) / 1000));
  }

  get codigoVencido(): boolean {
    return this.paso === 'codigo' && this.segundosParaVencer === 0;
  }

  get segundosBloqueo(): number {
    return Math.max(0, Math.ceil((this.bloqueoHasta - this.ahora) / 1000));
  }

  get ingresoBloqueado(): boolean {
    return this.segundosBloqueo > 0;
  }

  formatear(segundos: number): string {
    const m = Math.floor(segundos / 60);
    const s = segundos % 60;
    return `${m}:${s.toString().padStart(2, '0')}`;
  }

  /** Paso 1: validar el correo y pedir el código. */
  enviarCodigo(): void {
    this.error = '';
    const valor = this.form.controls.email.value.trim().toLowerCase();
    this.form.controls.email.setValue(valor);
    if (!valor) {
      this.form.markAllAsTouched();
      this.error = 'Ingrese su correo.';
      return;
    }
    if (this.form.controls.email.invalid || !valor.includes('@')) {
      this.form.markAllAsTouched();
      this.error = 'Ingrese un correo válido.';
      return;
    }
    this.loading = true;
    this.auth.solicitarCodigoAcceso(valor).subscribe({
      next: (res) => {
        this.loading = false;
        this.identificador = valor;
        this.form.controls.email.disable();
        this.iniciarContadores(res);
        this.paso = 'codigo';
        this.aviso = 'Enviamos un código de 6 dígitos a su correo. Revise también la carpeta de spam.';
        setTimeout(() => document.getElementById('codigo')?.focus(), 0);
      },
      error: (err: CodigoAccesoError) => {
        this.loading = false;
        if (err.segundosRestantes) {
          this.identificador = valor;
          this.paso = 'codigo';
          this.form.controls.email.disable();
          this.reenvioDesde = Date.now() + err.segundosRestantes * 1000;
          if (!this.venceEn) {
            this.venceEn = Date.now() + 5 * 60 * 1000;
          }
          this.arrancarReloj();
          this.aviso = 'Ya le enviamos un código hace poco. Ingréselo o espere para pedir otro.';
          return;
        }
        this.error = err.message;
      },
    });
  }

  /** Reenvía un código nuevo; el anterior deja de servir. */
  reenviar(): void {
    if (this.segundosParaReenviar > 0 || this.reenviando) {
      return;
    }
    this.error = '';
    this.reenviando = true;
    this.auth.solicitarCodigoAcceso(this.identificador).subscribe({
      next: (res) => {
        this.reenviando = false;
        this.codigoForm.reset({ codigo: '' });
        this.iniciarContadores(res);
        this.aviso = 'Le enviamos un código nuevo. El anterior ya no sirve.';
      },
      error: (err: CodigoAccesoError) => {
        this.reenviando = false;
        if (err.segundosRestantes) {
          this.reenvioDesde = Date.now() + err.segundosRestantes * 1000;
        }
        this.error = err.message;
      },
    });
  }

  /** Paso 2: validar el código e ingresar. */
  verificar(): void {
    this.error = '';
    if (this.ingresoBloqueado) {
      this.error = `Demasiados intentos fallidos. Espere ${this.formatear(this.segundosBloqueo)} para volver a intentar.`;
      return;
    }
    const codigo = this.codigoForm.controls.codigo.value.replace(/\D/g, '');
    this.codigoForm.controls.codigo.setValue(codigo);
    if (this.codigoVencido) {
      this.error = 'El código expiró. Solicite uno nuevo.';
      return;
    }
    if (!/^\d{6}$/.test(codigo)) {
      this.codigoForm.markAllAsTouched();
      this.error = 'Ingrese los 6 dígitos del código.';
      return;
    }
    this.loading = true;
    this.auth.verificarCodigoAcceso(this.identificador, codigo).subscribe({
      next: () => {
        this.detenerReloj();
        this.router.navigateByUrl('/inicio');
      },
      error: (err: CodigoAccesoError) => {
        this.loading = false;
        this.codigoForm.reset({ codigo: '' });
        if (err.bloqueo && err.segundosRestantes) {
          this.aplicarBloqueo(err.segundosRestantes);
        }
        this.error = err.message;
      },
    });
  }

  /** Deja solo números y envía automáticamente al completar los 6 dígitos. */
  alEscribirCodigo(): void {
    if (this.ingresoBloqueado) {
      return;
    }
    const control = this.codigoForm.controls.codigo;
    const limpio = control.value.replace(/\D/g, '').slice(0, 6);
    if (limpio !== control.value) {
      control.setValue(limpio);
    }
    if (limpio.length === 6 && !this.loading) {
      this.verificar();
    }
  }

  cambiarUsuario(): void {
    this.detenerReloj();
    this.form.controls.email.enable();
    this.paso = 'identificar';
    this.error = '';
    this.aviso = '';
    this.venceEn = 0;
    this.reenvioDesde = 0;
    this.bloqueoHasta = 0;
    this.codigoForm.enable();
    this.codigoForm.reset({ codigo: '' });
  }

  ngOnDestroy(): void {
    this.detenerReloj();
  }

  private iniciarContadores(res: SolicitudCodigoAcceso): void {
    const ahora = Date.now();
    this.venceEn = ahora + (res.segundosVigencia || 300) * 1000;
    this.reenvioDesde = ahora + (res.segundosReenvio || 4) * 1000;
    this.arrancarReloj();
  }

  private aplicarBloqueo(segundos: number): void {
    this.bloqueoHasta = Date.now() + Math.max(1, segundos) * 1000;
    this.codigoForm.disable();
    this.arrancarReloj();
  }

  private arrancarReloj(): void {
    this.ahora = Date.now();
    if (this.reloj) {
      return;
    }
    this.reloj = setInterval(() => {
      this.ahora = Date.now();
      if (this.bloqueoHasta && this.ahora >= this.bloqueoHasta) {
        this.bloqueoHasta = 0;
        if (this.paso === 'codigo') {
          this.codigoForm.enable();
          this.error = 'Ya puede intentar ingresar de nuevo.';
        }
      }
    }, 1000);
  }

  private detenerReloj(): void {
    if (this.reloj) {
      clearInterval(this.reloj);
      this.reloj = null;
    }
  }
}

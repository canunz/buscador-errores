import { Component, OnDestroy, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService, CodigoAccesoError, SolicitudCodigoAcceso } from '../../../core/services/auth.service';

type Paso = 'identificar' | 'codigo' | 'password';

/**
 * 1) el usuario escribe su correo;
 * 2) Administrador y Soporte ingresan con contraseña;
 * 3) el resto recibe un código de 6 dígitos al correo.
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
  mostrarClave = false;
  error = '';
  aviso = '';
  identificador = '';

  /** Momento en que vence el código y momento desde el que se puede reenviar. */
  private venceEn = 0;
  private reenvioDesde = 0;
  private ahora = Date.now();
  private reloj: ReturnType<typeof setInterval> | null = null;

  form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
  });

  codigoForm = this.fb.nonNullable.group({
    codigo: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]],
  });

  passwordForm = this.fb.nonNullable.group({
    password: ['', [Validators.required, Validators.minLength(6), Validators.maxLength(72)]],
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
        if (res.modo === 'PASSWORD') {
          this.paso = 'password';
          this.aviso = '';
          setTimeout(() => document.getElementById('password')?.focus(), 0);
          return;
        }
        this.iniciarContadores(res);
        this.paso = 'codigo';
        this.aviso = 'Enviamos un código de 6 dígitos a su correo. Revise también la carpeta de spam.';
        setTimeout(() => document.getElementById('codigo')?.focus(), 0);
      },
      error: (err: CodigoAccesoError) => {
        this.loading = false;
        if (err.segundosRestantes) {
          // Ya tiene un código reciente: se pasa directo a ingresarlo.
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

  ingresarConPassword(): void {
    this.error = '';
    const password = this.passwordForm.controls.password.value;
    if (this.passwordForm.invalid || !password) {
      this.passwordForm.markAllAsTouched();
      this.error = 'Ingrese su contraseña.';
      return;
    }
    this.loading = true;
    this.auth.login(this.identificador, password).subscribe({
      next: () => {
        this.router.navigateByUrl('/inicio');
      },
      error: (err: Error) => {
        this.loading = false;
        this.error = err.message || 'Correo o contraseña incorrectos.';
      },
    });
  }

  /** Paso 2: validar el código e ingresar. */
  verificar(): void {
    this.error = '';
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
      error: (err: Error) => {
        this.loading = false;
        this.error = err.message;
        this.codigoForm.reset({ codigo: '' });
      },
    });
  }

  /** Deja solo números y envía automáticamente al completar los 6 dígitos. */
  alEscribirCodigo(): void {
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
    this.mostrarClave = false;
    this.codigoForm.reset({ codigo: '' });
    this.passwordForm.reset({ password: '' });
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

  private arrancarReloj(): void {
    this.ahora = Date.now();
    if (this.reloj) {
      return;
    }
    this.reloj = setInterval(() => (this.ahora = Date.now()), 1000);
  }

  private detenerReloj(): void {
    if (this.reloj) {
      clearInterval(this.reloj);
      this.reloj = null;
    }
  }
}

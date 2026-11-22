import { Component, OnDestroy, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { AuthService, CodigoAccesoError, SolicitudCodigoAcceso } from '../../../core/services/auth.service';

type Paso = 'identificar' | 'codigo';

/**
 * 1) el usuario escribe su correo;
 * 2) una cuenta habilitada recibe un código;
 * 3) todos los roles verifican el código antes de iniciar sesión.
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

  /** Momento en que vence el código y momento desde el que se puede reenviar. */
  private venceEn = 0;
  private reenvioDesde = 0;
  /** Momento hasta el que el ingreso queda bloqueado por equivocarse varias veces con el código. */
  private bloqueadoHasta = 0;
  private ahora = Date.now();
  private reloj: ReturnType<typeof setInterval> | null = null;

  form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
  });

  /** Cuando la persona corrige el correo, se borra el error anterior (por ejemplo, "ese correo no existe"). */
  private readonly limpiarErrorAlEscribir = this.form.controls.email.valueChanges
    .pipe(takeUntilDestroyed())
    .subscribe(() => {
      if (this.paso === 'identificar' && !this.loading) {
        this.error = '';
      }
    });

  codigoForm = this.fb.nonNullable.group({
    codigo: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]],
  });

  /**
   * Revisa el correo mientras se escribe y dice qué le falta.
   * Devuelve un texto vacío cuando el correo está bien escrito.
   */
  get problemaCorreo(): string {
    return LoginComponent.revisarCorreo(this.form.controls.email.value);
  }

  /** Se muestra el aviso solo cuando la persona ya escribió algo. */
  get mostrarProblemaCorreo(): boolean {
    return this.paso === 'identificar' && !!this.form.controls.email.value.trim() && !!this.problemaCorreo;
  }

  get segundosParaVencer(): number {
    return Math.max(0, Math.ceil((this.venceEn - this.ahora) / 1000));
  }

  get segundosParaReenviar(): number {
    return Math.max(0, Math.ceil((this.reenvioDesde - this.ahora) / 1000));
  }

  /** Segundos que faltan para que termine el bloqueo; 0 si puede intentar. */
  get segundosBloqueo(): number {
    return Math.max(0, Math.ceil((this.bloqueadoHasta - this.ahora) / 1000));
  }

  get codigoVencido(): boolean {
    return this.paso === 'codigo' && this.segundosParaVencer === 0;
  }

  formatear(segundos: number): string {
    const m = Math.floor(segundos / 60);
    const s = segundos % 60;
    return `${m}:${s.toString().padStart(2, '0')}`;
  }

  get lineasAviso(): string[] {
    return this.aviso
      .split(/\n+/)
      .map((linea) => linea.trim())
      .filter(Boolean);
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
    const problema = LoginComponent.revisarCorreo(valor);
    if (problema) {
      this.form.markAllAsTouched();
      this.error = problema;
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
        this.aviso = 'Si la cuenta está habilitada, recibirá un código.\nRevise también la carpeta de spam.';
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
          this.aviso = 'Espere antes de pedir otro código.';
          return;
        }
        this.error = err.message;
        if (err.correoNoRegistrado) {
          // El correo no existe: no se envía nada y se deja el campo listo para escribir otro.
          setTimeout(() => {
            const campo = document.getElementById('email') as HTMLInputElement | null;
            campo?.focus();
            campo?.select();
          }, 0);
        }
      },
    });
  }

  /**
   * Reglas del correo, en el orden en que la persona lo va escribiendo.
   * Ej.: "fsdkj@" -> falta lo que va después del @; "fjksdgmail.com" -> falta el @.
   */
  static revisarCorreo(texto: string): string {
    const valor = (texto ?? '').trim();
    if (!valor) return 'Ingrese su correo.';
    if (/\s/.test(valor)) return 'El correo no puede tener espacios.';

    const arrobas = valor.split('@').length - 1;
    if (arrobas === 0) return 'Falta el @. Ejemplo: nombre@gmail.com';
    if (arrobas > 1) return 'El correo solo puede tener un @.';

    const [usuario, dominio] = valor.split('@');
    if (!usuario) return 'Escriba su nombre antes del @.';
    if (!dominio) return 'Falta lo que va después del @. Ejemplo: gmail.com';
    if (!dominio.includes('.')) return 'Al dominio le falta el punto. Ejemplo: gmail.com';

    const partes = dominio.split('.');
    if (partes.some((parte) => !parte)) return 'El dominio está incompleto. Ejemplo: gmail.com';
    if (!/^[a-z]{2,}$/i.test(partes[partes.length - 1])) return 'La terminación del correo está incompleta. Ejemplo: .com o .cl';
    if (!/^[a-z0-9._%+-]+$/i.test(usuario) || !/^[a-z0-9.-]+$/i.test(dominio)) {
      return 'El correo tiene caracteres que no son válidos.';
    }
    return '';
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
        this.aviso = 'Solicitud recibida. Si corresponde emitir otro código, el anterior dejará de servir.';
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
    const codigo = this.codigoForm.controls.codigo.value.replace(/\D/g, '');
    this.codigoForm.controls.codigo.setValue(codigo);
    if (this.segundosBloqueo > 0) {
      // Mientras dure el bloqueo no se envía nada al servidor.
      return;
    }
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
        this.router.navigateByUrl(this.auth.accesoBasico() ? '/solicitudes' : '/inicio');
      },
      error: (err: CodigoAccesoError) => {
        this.loading = false;
        this.codigoForm.reset({ codigo: '' });
        if (err.segundosRestantes) {
          // Se equivocó demasiadas veces: se muestra la cuenta regresiva en vez del error.
          this.bloqueadoHasta = Date.now() + err.segundosRestantes * 1000;
          this.error = '';
          this.arrancarReloj();
          return;
        }
        this.error = err.message;
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
    if (limpio.length === 6 && !this.loading && this.segundosBloqueo === 0) {
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
    this.bloqueadoHasta = 0;
    this.codigoForm.reset({ codigo: '' });
  }

  ngOnDestroy(): void {
    this.detenerReloj();
  }

  private iniciarContadores(res: SolicitudCodigoAcceso): void {
    const ahora = Date.now();
    this.venceEn = ahora + (res.segundosVigencia || 300) * 1000;
    this.reenvioDesde = ahora + (res.segundosReenvio || 60) * 1000;
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

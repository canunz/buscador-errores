import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, catchError, map, tap, throwError } from 'rxjs';
import { apiUrl } from '../config/api';
import { AuthSession, Usuario, esAdministrador, esSoporte, esStaff } from '../models/usuario.model';

const STORAGE_KEY = 'dimabug.auth';

interface LoginResponse {
  accesoBasico?: boolean;
  token: string;
  usuarioId: number;
  nombre: string;
  email: string;
  rol: string;
}

export interface SolicitudCodigoAcceso {
  message: string;
  segundosVigencia: number;
  segundosReenvio: number;
  modo?: 'CODIGO' | string;
}

/** Error del ingreso con código; en un reenvío anticipado indica cuántos segundos faltan. */
/** Error del ingreso con código. Puede traer los segundos de espera o avisar que el correo no existe. */
export type CodigoAccesoError = Error & { segundosRestantes?: number; correoNoRegistrado?: boolean };

interface AuthMeResponse {
  email: string;
  estado: string;
  accesoBasico?: boolean;
}

/**
 * Servicio de sesión: login con código, guardar la sesión en el navegador,
 * saber el rol del usuario y cerrar sesión.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  // Sesión actual; al abrir la página se lee la que quedó guardada
  private readonly sessionSignal = signal<AuthSession | null>(this.readSession());

  readonly session = this.sessionSignal.asReadonly();
  readonly usuario = computed(() => this.sessionSignal()?.usuario ?? null);
  // Hay sesión si existe token y usuario
  readonly isAuthenticated = computed(() => {
    const session = this.sessionSignal();
    return !!session?.token && !!session.usuario;
  });
  readonly accesoBasico = computed(() => this.usuario()?.accesoBasico === true);
  // Roles del usuario, para mostrar u ocultar opciones
  readonly isAdmin = computed(() => esAdministrador(this.sessionSignal()?.usuario));
  readonly isSoporte = computed(() => esSoporte(this.sessionSignal()?.usuario));
  readonly isStaff = computed(() => esStaff(this.sessionSignal()?.usuario));

  /**
   * Paso 1 del ingreso: valida que el correo exista y le envía un código de acceso.
   * También se usa para reenviar el código.
   */
  solicitarCodigoAcceso(correo: string): Observable<SolicitudCodigoAcceso> {
    const valor = correo.trim();
    return this.http
      .post<SolicitudCodigoAcceso>(apiUrl('/auth/codigo'), { email: valor })
      .pipe(catchError((err: HttpErrorResponse) => throwError(() => this.codigoError(err, 'solicitar'))));
  }

  /** Paso 2 del ingreso: valida el código recibido y abre la sesión. */
  verificarCodigoAcceso(correo: string, codigo: string): Observable<AuthSession> {
    return this.http
      .post<LoginResponse>(apiUrl('/auth/codigo/verificar'), {
        email: correo.trim(),
        codigo: codigo.replace(/\s/g, ''),
      })
      .pipe(
        map((res) => this.sessionFromLogin(res)),
        tap((session) => this.persist(session)),
        catchError((err: HttpErrorResponse) => throwError(() => this.codigoError(err, 'verificar'))),
      );
  }

  /** Pregunta al backend si la sesión sigue válida. */
  me(): Observable<AuthMeResponse> {
    return this.http.get<AuthMeResponse>(apiUrl('/auth/me')).pipe(
      catchError((err: HttpErrorResponse) =>
        throwError(() => new Error(this.httpError(err, 'No fue posible comprobar la autenticación.'))),
      ),
    );
  }

  /** Cierra sesión: borra el token y vuelve al login. */
  logout(): void {
    localStorage.removeItem(STORAGE_KEY);
    this.sessionSignal.set(null);
    this.router.navigateByUrl('/login');
  }

  /** Actualiza los datos visibles de la sesión sin tocar el token. */
  actualizarSesion(parcial: Partial<Usuario>): void {
    const session = this.sessionSignal();
    if (!session) {
      return;
    }
    this.persist({
      ...session,
      usuario: { ...session.usuario, ...parcial },
    });
  }

  /** Devuelve el token para que el interceptor lo agregue. */
  token(): string | undefined {
    return this.sessionSignal()?.token;
  }

  private sessionFromLogin(res: LoginResponse): AuthSession {
    if (!res?.token) {
      throw new Error('La API no devolvió un token de autenticación.');
    }

    const usuario: Usuario = {
      accesoBasico: res.accesoBasico === true,
      usuarioId: Number(res.usuarioId),
      usuarioNombre: res.nombre,
      usuarioEmail: res.email,
      usuarioEstado: true,
      rolNombre: res.rol,
      rol: res.rol ? { rolId: 0, rolNombre: res.rol } : undefined,
    };

    return { token: res.token, usuario };
  }

  private codigoError(err: HttpErrorResponse, paso: 'solicitar' | 'verificar'): CodigoAccesoError {
    const error = new Error(this.codigoMensaje(err, paso)) as CodigoAccesoError;
    if (err.status === 429) {
      error.segundosRestantes = Number(err.error?.segundosRestantes) || 60;
    }
    if (paso === 'solicitar' && err.status === 404) {
      error.correoNoRegistrado = true;
    }
    return error;
  }

  private codigoMensaje(err: HttpErrorResponse, paso: 'solicitar' | 'verificar'): string {
    if (err.status === 400) {
      return paso === 'solicitar'
        ? 'Ingrese un correo válido.'
        : this.safeApiMessage(this.extractApiMessage(err), 'Revise los datos ingresados.');
    }
    if (paso === 'solicitar' && err.status === 404) {
      return this.safeApiMessage(this.extractApiMessage(err), 'Ese correo no existe en DimaBug. Intente nuevamente con otro correo.');
    }
    if (paso === 'solicitar' && err.status === 401) {
      // El endpoint no responde: el backend sigue corriendo con la versión anterior.
      return 'El servidor aún no tiene activo el ingreso con código. Reinicie el backend con una compilación limpia.';
    }
    if (err.status === 401) {
      return 'El código no es válido o ya expiró. Solicite uno nuevo.';
    }
    if (err.status === 403) {
      return 'Su cuenta no tiene autorización o está inactiva.';
    }
    if (err.status === 429) {
      return this.safeApiMessage(this.extractApiMessage(err), 'Espere un momento antes de pedir otro código.');
    }
    if (err.status === 503) {
      return this.safeApiMessage(
        this.extractApiMessage(err),
        'No fue posible enviar el código. Intente nuevamente en unos minutos.',
      );
    }
    if (err.status === 0) {
      return 'No se pudo conectar con el servidor. Confirme que el backend esté encendido.';
    }
    return this.httpError(err, 'No fue posible iniciar sesión. Intente nuevamente.');
  }

  private recuperarMensaje(err: HttpErrorResponse, paso: 'enviar' | 'validar' | 'guardar'): string {
    if (err.status === 404) {
      return this.safeApiMessage(this.extractApiMessage(err), 'Este correo no está registrado en DimaBug.');
    }
    if (err.status === 403) {
      return 'Su cuenta no tiene autorización o está inactiva.';
    }
    if (err.status === 401) {
      return paso === 'validar' || paso === 'guardar'
        ? 'El código no es válido o ya expiró. Solicite uno nuevo.'
        : this.safeApiMessage(this.extractApiMessage(err), 'No fue posible enviar el código. Reinicie el backend e intente de nuevo.');
    }
    if (err.status === 429) {
      return this.safeApiMessage(this.extractApiMessage(err), 'Espere un momento antes de pedir otro código.');
    }
    if (err.status === 503) {
      return this.safeApiMessage(this.extractApiMessage(err), 'No fue posible enviar el código. Intente en unos minutos.');
    }
    if (err.status === 0) {
      return 'No se pudo conectar con el servidor. Confirme que el backend esté encendido.';
    }
    const fallback =
      paso === 'enviar'
        ? 'No fue posible enviar el código. Intente más tarde.'
        : paso === 'validar'
          ? 'El código ingresado no es válido.'
          : 'No fue posible actualizar la contraseña. Intente más tarde.';
    return this.httpError(err, fallback);
  }

  private httpError(err: HttpErrorResponse, fallback: string): string {
    if (err.status === 400) {
      return this.safeApiMessage(this.extractApiMessage(err), 'Los datos enviados no son válidos.');
    }
    if (err.status === 401) {
      return 'No autenticado o token inválido.';
    }
    if (err.status === 403) {
      return 'No tiene autorización para esta operación.';
    }
    if (err.status === 404) {
      return fallback;
    }
    if (err.status === 0) {
      return 'No se pudo conectar con el servidor. Intente más tarde.';
    }
    return this.safeApiMessage(this.extractApiMessage(err), fallback);
  }

  private extractApiMessage(err: HttpErrorResponse): unknown {
    if (typeof err.error === 'string') {
      return err.error;
    }
    if (err.error && typeof err.error === 'object') {
      return err.error.message || err.error.error;
    }
    return undefined;
  }

  /** Evita filtrar mensajes técnicos (puertos, stack, endpoints) al usuario. */
  private safeApiMessage(raw: unknown, fallback: string): string {
    if (typeof raw !== 'string') {
      return fallback;
    }
    const msg = raw.trim();
    if (!msg) {
      return fallback;
    }
    const technical =
      /spring|boot|:8080|:8081|localhost|\/api\/|httpd|apache|proxy|endpoint|stack|exception|nullpointer|^not found$|^unauthorized$|^forbidden$/i;
    if (technical.test(msg) || msg.length > 160) {
      return fallback;
    }
    return msg;
  }

  /** Guarda la sesión en el navegador. */
  private persist(session: AuthSession): void {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    this.sessionSignal.set(session);
  }

  /** Lee la sesión guardada; si está incompleta, la descarta. */
  private readSession(): AuthSession | null {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (!raw) {
        return null;
      }
      const session = JSON.parse(raw) as AuthSession;
      if (!session?.token || !session.usuario) {
        localStorage.removeItem(STORAGE_KEY);
        return null;
      }
      return session;
    } catch {
      return null;
    }
  }
}

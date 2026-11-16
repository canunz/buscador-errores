export type ModalidadPaso = 'INFORMATIVO' | 'MANUAL_GUIADO' | 'SOLICITAR_EVIDENCIA' | 'ENVIAR_CORREO' | 'AUTOMATICO' | 'MANUAL' | 'VALIDACION_EXTERNA';
export interface DestinatarioPaso { correo: string; tipo: 'PRINCIPAL' | 'COPIA'; }
export interface ConfiguracionPaso {
  asunto?: string | null;
  destinatarios: DestinatarioPaso[]; usarDestinatariosSucursal: boolean; mensaje: string | null;
  evidenciasRequeridas: string[]; requiereAprobacion: boolean; recordatoriosHoras: number[];
  plazoRespuestaHoras: number; condicionFinalizacion: string; accionAutomatica: string | null;
}
export function modalidadVisible(modo?: string): ModalidadPaso {
  return modo === 'VALIDACION_EXTERNA' ? 'SOLICITAR_EVIDENCIA' : !modo || modo === 'MANUAL' ? 'MANUAL_GUIADO' : modo as ModalidadPaso;
}
export function configuracionInicial(modo: ModalidadPaso, critico: boolean, actual?: Partial<ConfiguracionPaso> | null): ConfiguracionPaso {
  const evidencia = modalidadVisible(modo) === 'SOLICITAR_EVIDENCIA';
  return { asunto: actual?.asunto ?? (evidencia ? "Solicitud de fotografías" : "Solicitud del procedimiento"), destinatarios: actual?.destinatarios ?? [], usarDestinatariosSucursal: actual?.usarDestinatariosSucursal ?? modo !== 'ENVIAR_CORREO',
    mensaje: actual?.mensaje ?? null, evidenciasRequeridas: actual?.evidenciasRequeridas ?? (evidencia ? ['MUEBLE', 'RED', 'ELECTRICIDAD'] : []),
    requiereAprobacion: critico || (actual?.requiereAprobacion ?? evidencia), recordatoriosHoras: actual?.recordatoriosHoras ?? [24,72],
    plazoRespuestaHoras: actual?.plazoRespuestaHoras ?? 120,
    condicionFinalizacion: actual?.condicionFinalizacion ?? (evidencia ? 'EVIDENCIAS_APROBADAS' : modo === 'ENVIAR_CORREO' ? 'ENVIO_REGISTRADO' : modo === 'AUTOMATICO' ? 'ACCION_EXITOSA' : 'CONFIRMACION_USUARIO'),
    accionAutomatica: actual?.accionAutomatica ?? (modo === 'AUTOMATICO' ? 'VALIDAR_CONTEXTO_SUCURSAL' : null) };
}

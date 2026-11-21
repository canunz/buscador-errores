/** Misma limpieza y limite del adaptador Angular, sin APIs de plataforma. */
export function normalizarTextoOcr(original: string): string {
  const texto = original.replace(/[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/g,'').trim();
  if (texto.length > 20_000)
    throw new Error('La imagen contiene demasiado texto. Recorta el mensaje de error.');
  return texto;
}

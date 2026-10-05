import {
  MAX_MATERIAL_BYTES,
  admiteUploadLocal,
  crearFormDataMaterial,
  descargarMaterialAutenticado,
  esUrlDescargaAutenticada,
  mimeCompatibleConTipo,
  validarMaterialFormulario,
} from './material-archivo';
import { HttpClient, HttpHeaders, HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';

function fakeFile(name: string, type: string, size = 12): File {
  const blob = new Blob([new Uint8Array(size)], { type });
  return new File([blob], name, { type });
}

describe('material-archivo', () => {
  it('admite adjunto para PDF, IMAGEN y VIDEO', () => {
    expect(admiteUploadLocal('PDF')).toBeTrue();
    expect(admiteUploadLocal('IMAGEN')).toBeTrue();
    expect(admiteUploadLocal('VIDEO')).toBeTrue();
    expect(admiteUploadLocal('ENLACE')).toBeFalse();
  });

  it('reconoce URL autenticada y no expone file:', () => {
    expect(esUrlDescargaAutenticada('/api/conocimientos/4/materiales/2/archivo')).toBeTrue();
    expect(esUrlDescargaAutenticada('/api/procedimientos/1/pasos/5/materiales/8/archivo')).toBeTrue();
    expect(esUrlDescargaAutenticada('https://docs.ejemplo.cl/manual.pdf')).toBeFalse();
    expect(esUrlDescargaAutenticada('file:materiales/9d50c602-4bf2-438a-82c3-e197c9153250')).toBeFalse();
  });

  it('valida MIME de PDF e imagen', () => {
    expect(mimeCompatibleConTipo('PDF', 'application/pdf')).toBeTrue();
    expect(mimeCompatibleConTipo('PDF', 'image/png')).toBeFalse();
    expect(mimeCompatibleConTipo('IMAGEN', 'image/png')).toBeTrue();
    expect(mimeCompatibleConTipo('IMAGEN', 'image/jpeg')).toBeTrue();
    expect(mimeCompatibleConTipo('IMAGEN', 'image/webp')).toBeTrue();
    expect(mimeCompatibleConTipo('IMAGEN', 'application/pdf')).toBeFalse();
    expect(mimeCompatibleConTipo('VIDEO', 'video/mp4')).toBeTrue();
    expect(mimeCompatibleConTipo('VIDEO', 'application/pdf')).toBeFalse();
  });

  it('exige nombre y tope de 150', () => {
    expect(
      validarMaterialFormulario({
        nombre: '',
        tipo: 'ENLACE',
        origen: 'enlace',
        url: 'https://x',
      }),
    ).toContain('nombre');
    expect(
      validarMaterialFormulario({
        nombre: 'a'.repeat(151),
        tipo: 'ENLACE',
        origen: 'enlace',
        url: 'https://x',
      }),
    ).toContain('150');
  });

  it('exige URL cuando origen es enlace', () => {
    expect(
      validarMaterialFormulario({
        nombre: 'Manual',
        tipo: 'ENLACE',
        origen: 'enlace',
        url: '',
      }),
    ).toContain('URL');
    expect(
      validarMaterialFormulario({
        nombre: 'Manual',
        tipo: 'ENLACE',
        origen: 'enlace',
        url: 'https://x',
      }),
    ).toBeNull();
  });

  it('exige archivo cuando origen es archivo', () => {
    expect(
      validarMaterialFormulario({
        nombre: 'Manual',
        tipo: 'PDF',
        origen: 'archivo',
      }),
    ).toContain('archivo');
  });

  it('acepta PDF e imagen dentro de 10 MiB', () => {
    expect(
      validarMaterialFormulario({
        nombre: 'Guía',
        tipo: 'PDF',
        origen: 'archivo',
        archivo: fakeFile('a.pdf', 'application/pdf'),
      }),
    ).toBeNull();
    expect(
      validarMaterialFormulario({
        nombre: 'Foto',
        tipo: 'IMAGEN',
        origen: 'archivo',
        archivo: fakeFile('a.png', 'image/png'),
      }),
    ).toBeNull();
  });

  it('rechaza archivo mayor a 10 MiB', () => {
    const grande = fakeFile('a.pdf', 'application/pdf', MAX_MATERIAL_BYTES + 1);
    expect(
      validarMaterialFormulario({
        nombre: 'Guía',
        tipo: 'PDF',
        origen: 'archivo',
        archivo: grande,
      }),
    ).toContain('10 MiB');
  });

  it('rechaza MIME incompatible', () => {
    expect(
      validarMaterialFormulario({
        nombre: 'Guía',
        tipo: 'PDF',
        origen: 'archivo',
        archivo: fakeFile('a.txt', 'text/plain'),
      }),
    ).toContain('pdf');
  });

  it('en VIDEO exige URL o archivo, y ENLACE solo URL', () => {
    expect(
      validarMaterialFormulario({ nombre: 'V', tipo: 'VIDEO', origen: 'enlace', url: '' }),
    ).toContain('URL o adjunte');
    expect(
      validarMaterialFormulario({
        nombre: 'V',
        tipo: 'VIDEO',
        origen: 'enlace',
        url: 'https://youtu.be/x',
      }),
    ).toBeNull();
    expect(
      validarMaterialFormulario({
        nombre: 'V',
        tipo: 'VIDEO',
        origen: 'archivo',
        archivo: fakeFile('clip.mp4', 'video/mp4'),
      }),
    ).toBeNull();
    expect(
      validarMaterialFormulario({
        nombre: 'E',
        tipo: 'ENLACE',
        origen: 'enlace',
        url: 'https://docs.cl',
      }),
    ).toBeNull();
  });

  it('arma FormData con nombre, tipo y archivo sin Content-Type', () => {
    const file = fakeFile('manual.pdf', 'application/pdf');
    const data = crearFormDataMaterial(' Manual ', 'PDF', file);
    expect(data.get('nombre')).toBe('Manual');
    expect(data.get('tipo')).toBe('PDF');
    const adjunto = data.get('archivo') as File;
    expect(adjunto).toBeInstanceOf(File);
    expect(adjunto.name).toBe('manual.pdf');
    expect(adjunto.type).toBe('application/pdf');
    expect((data as unknown as { headers?: unknown }).headers).toBeUndefined();
  });

  it('descarga con HttpClient blob y libera object URL', () => {
    const blob = new Blob(['%PDF'], { type: 'application/pdf' });
    const http = {
      get: jasmine.createSpy('get').and.returnValue(
        of(
          new HttpResponse({
            body: blob,
            headers: new HttpHeaders({ 'Content-Disposition': 'attachment; filename="Guia.pdf"' }),
          }),
        ),
      ),
    } as unknown as HttpClient;
    const create = spyOn(URL, 'createObjectURL').and.returnValue('blob:local');
    const revoke = spyOn(URL, 'revokeObjectURL');
    const click = jasmine.createSpy('click');
    spyOn(document, 'createElement').and.callFake((tag: string) => {
      if (tag === 'a') {
        return { href: '', download: '', rel: '', click, remove() {}, } as unknown as HTMLAnchorElement;
      }
      return document.createElement(tag);
    });
    spyOn(document.body, 'appendChild');

    descargarMaterialAutenticado(
      http,
      '/api/conocimientos/1/materiales/3/archivo',
      'Guia',
      'PDF',
    ).subscribe();

    expect(http.get).toHaveBeenCalledWith(
      '/api/conocimientos/1/materiales/3/archivo',
      jasmine.objectContaining({ responseType: 'blob', observe: 'response' }),
    );
    expect(create).toHaveBeenCalledWith(blob);
    expect(click).toHaveBeenCalled();
    expect(revoke).toHaveBeenCalledWith('blob:local');
  });
});

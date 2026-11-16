import { DestinatariosEditorComponent } from './destinatarios-editor.component';

describe('Correos reutilizables: editor de destinatarios', () => {
  let editor: DestinatariosEditorComponent;
  beforeEach(() => { editor = new DestinatariosEditorComponent(); });
  it('agrega un correo normalizado y conserva el tipo en copia', () => {
    spyOn(editor.valueChange, 'emit'); editor.correo = ' QA@DIMARSA.CL '; editor.tipo = 'COPIA';
    editor.agregar();
    expect(editor.valueChange.emit).toHaveBeenCalledWith([{ correo: 'qa@dimarsa.cl', tipo: 'COPIA' }]);
  });
  it('rechaza duplicados, dominios distintos y direcciones inválidas', () => {
    spyOn(editor.valueChange, 'emit'); editor.value = [{ correo: 'qa@dimarsa.cl', tipo: 'PRINCIPAL' }];
    for (const correo of ['QA@DIMARSA.CL', 'qa@otro.cl', '.qa@dimarsa.cl', 'qa..uno@dimarsa.cl', 'qa@sub.dimarsa.cl']) {
      editor.correo = correo; editor.agregar(); expect(editor.error).toBeTruthy();
    }
    expect(editor.valueChange.emit).not.toHaveBeenCalled();
  });
  it('elimina solo el correo seleccionado y respeta el bloqueo de guardado', () => {
    spyOn(editor.valueChange, 'emit'); editor.value = [{ correo: 'qa@dimarsa.cl', tipo: 'PRINCIPAL' }];
    editor.disabled = true; editor.quitar('qa@dimarsa.cl'); expect(editor.valueChange.emit).not.toHaveBeenCalled();
    editor.disabled = false; editor.quitar('qa@dimarsa.cl'); expect(editor.valueChange.emit).toHaveBeenCalledWith([]);
  });
});

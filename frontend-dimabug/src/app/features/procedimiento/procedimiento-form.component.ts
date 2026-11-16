import { ClasificacionService } from '../../core/services/clasificacion.service';
import { CatalogoRef } from '../../core/models/conocimiento.model';
import { Procedimiento, EstadoProcedimiento } from '../../core/models/procedimiento.model';
import { Component, HostListener, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ProcedimientoService } from '../../core/services/procedimiento.service';
import { LoadingModalComponent } from '../../shared/ui/loading-modal.component';
@Component({ selector: 'app-procedimiento-form', standalone: true, imports: [ReactiveFormsModule, RouterLink, LoadingModalComponent],
  templateUrl: './procedimiento-form.component.html', styleUrl: './procedimiento-form.component.css' })
export class ProcedimientoFormComponent implements OnInit {
  private readonly procedimientos = inject(ProcedimientoService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly clasificacion=inject(ClasificacionService);
  hardwares:CatalogoRef[]=[];cargandoHardware=false;errorCatalogo='';revision=1;
  private readonly fb = inject(FormBuilder);
  editId: number | null = null;
  confirmadoId: number | null = null;
  loading = false; guardando = false; error = ''; mensaje = ''; soloLectura = false;
  cargaValida = false; identificadorInvalido = false; creacionIncierta = false;
  private inicial = { nombre:'',descripcion:'',hardwareId:null as number|null,informacionComplementaria:'',estado:'BORRADOR' as EstadoProcedimiento };
  form=this.fb.nonNullable.group({nombre:['',[Validators.required,Validators.pattern(/\S/),Validators.maxLength(150)]],descripcion:['',[Validators.maxLength(20000)]],hardwareId:this.fb.control<number|null>(null,[Validators.min(1)]),informacionComplementaria:['',[Validators.maxLength(10000)]],estado:this.fb.nonNullable.control<EstadoProcedimiento>('BORRADOR')});
  ngOnInit(): void {
    this.cargarHardware();
    const raw = this.route.snapshot.paramMap.get('id');
    if (raw == null) { this.cargaValida = true; return; }
    const id = Number(raw);
    if (!Number.isInteger(id) || id < 1 || id > 2147483647) { this.identificadorInvalido = true; this.error = 'El identificador del procedimiento no es válido.'; this.form.disable(); return; }
    this.editId = id; this.cargar();
  }
  cargar(): void {
    if (!this.editId || this.loading || this.guardando || this.identificadorInvalido) return;
    this.loading = true; this.cargaValida = false; this.error = ''; this.form.disable();
    this.procedimientos.obtenerPorId(this.editId).subscribe({
      next: item => {
        if (item.id !== this.editId) { this.loading = false; this.error = 'La respuesta no corresponde al procedimiento solicitado.'; return; }
        this.procedimientos.prepararEdicion(item.id).subscribe({
          next:borrador=>{this.editId=borrador.id;this.aplicar(borrador);if(borrador.id!==item.id){this.mensaje='Se abrió una revisión en borrador. Las ejecuciones anteriores conservan su versión.';void this.router.navigate(['/procedimientos',borrador.id,'editar'],{replaceUrl:true}).catch(()=>{this.error='La revisión está preparada. Puedes seguir editándola aquí.';});}},
          error:(e:Error)=>{this.loading=false;this.error=e.message;}
        });
      },
      error: (e: Error) => { this.loading = false; this.error = `No se cargó el procedimiento. No se habilitó la edición. ${e.message}`; },
    });
  }
  private aplicar(item:Procedimiento):void{
    this.soloLectura=false;this.revision=item.revision??1;
    this.inicial={nombre:item.nombre,descripcion:item.descripcion??'',hardwareId:item.hardwareId??null,informacionComplementaria:item.informacionComplementaria??'',estado:item.estado};
    this.form.reset(this.inicial);this.cargaValida=true;this.loading=false;this.form.enable();
  }
  cargarHardware():void{
    if(this.cargandoHardware)return;this.cargandoHardware=true;this.errorCatalogo='';
    this.clasificacion.listarHardware(true).subscribe({next:h=>{this.hardwares=h;this.cargandoHardware=false;},error:()=>{this.cargandoHardware=false;this.errorCatalogo='No se pudo cargar el catálogo de hardware. Puedes reintentar o guardar sin hardware.';}});
  }
  get hardwareActualNoListado():boolean{const id=this.form.controls.hardwareId.value;return id!==null && !this.hardwares.some(h=>h.id===id);}
  get errorHardware():string{return this.form.controls.hardwareId.invalid?'Selecciona un hardware válido o Sin hardware.':'';}
  get errorNombre(): string {
    const c = this.form.controls.nombre;
    if (c.hasError('maxlength')) return 'El nombre no puede superar 150 caracteres.';
    return c.invalid ? 'El nombre es obligatorio y no puede contener solo espacios.' : '';
  }
  tieneCambiosPendientes(): boolean {
    if (this.creacionIncierta) return true;
    if (!this.cargaValida || this.soloLectura) return false;
    const v = this.form.getRawValue(); return JSON.stringify(v)!==JSON.stringify(this.inicial);
  }
  operacionEnCurso(): boolean { return this.guardando; }
  @HostListener('window:beforeunload', ['$event']) antesDeSalir(event: BeforeUnloadEvent): void {
    if (this.guardando || this.tieneCambiosPendientes()) { event.preventDefault(); event.returnValue = ''; }
  }
  guardar(): void {
    if (!this.cargaValida || this.soloLectura || this.guardando || this.creacionIncierta) return;
    if (this.form.invalid) { this.form.markAllAsTouched(); this.error = this.errorNombre || this.errorHardware || 'Revisa los campos del formulario.'; return; }
    const v = this.form.getRawValue(); this.guardando = true; this.error = ''; this.mensaje = '';
    const llamada = this.editId ? this.procedimientos.actualizar(this.editId, v) : this.procedimientos.crear(v);
    llamada.subscribe({
      next: item => {
        this.guardando = false; this.confirmadoId = item.id; this.editId = item.id;
        this.aplicar(item);
        this.mensaje = `Procedimiento ${item.id} guardado. Puede continuar agregando pasos en su detalle.`;
        this.irAlDetalle();
      },
      error: (e: Error & { status?: number }) => {
        this.guardando = false;
        if (!this.editId && (e.status == null || e.status === 0 || e.status >= 500)) {
          this.creacionIncierta = true; this.error = `No se pudo verificar el resultado del alta. Los datos se conservan. Revise el listado antes de crear otro procedimiento. ${e.message}`;
        } else this.error = `No se confirmó el guardado; sus datos siguen pendientes. ${e.message}`;
      },
    });
  }
  irAlDetalle(): void {
    if (!this.confirmadoId) return;
    void this.router.navigate(['/procedimientos', this.confirmadoId]).then(ok => {
      if (!ok) this.error = 'El procedimiento ya está guardado. No fue posible abrir el detalle; use Continuar.';
    }).catch(() => { this.error = 'El procedimiento ya está guardado. No fue posible abrir el detalle; use Continuar.'; });
  }
}
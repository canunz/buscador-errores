import { Injectable,inject } from '@angular/core';
import { HttpClient,HttpErrorResponse } from '@angular/common/http';
import { catchError,throwError,map,Observable } from 'rxjs';
import { apiUrl } from '../config/api';
import { plazoConsulta } from '../http/plazo-consulta';
import { TipoMaterial } from '../models/conocimiento.model';
import { crearFormDataMaterial,iniciarDescargaBlob } from '../material/material-archivo';
import type { MaterialApoyoFormValue } from '../../shared/ui/material-apoyo-form.component';
export interface SolucionPublica{id:number;titulo:string;instrucciones:string;tipo:string;}
export interface MaterialSolucion{id:number;nombre:string;tipo:TipoMaterial;url:string;publicado:boolean;}
@Injectable({providedIn:'root'})
export class AutoservicioService {
 private readonly http=inject(HttpClient);
 private error<T>(){return catchError<T,Observable<never>>((e:HttpErrorResponse)=>throwError(()=>new Error(e.status===429?'Demasiadas consultas. Intenta en un minuto.':e.status===404?'El recurso no está disponible.':'No fue posible completar la operación. Intenta nuevamente.')));}
 buscar(q:string){return this.http.get<SolucionPublica[]>(apiUrl('/publico/soluciones'),{params:{q}}).pipe(plazoConsulta(),this.error());}
 detalle(id:number){return this.http.get<SolucionPublica>(apiUrl('/publico/soluciones/'+id)).pipe(plazoConsulta(),this.error());}
 materiales(id:number,admin=false){return this.http.get<MaterialSolucion[]>(apiUrl(`/${admin?'admin':'publico'}/soluciones/${id}/materiales`)).pipe(plazoConsulta(),this.error());}
 crear(id:number,v:MaterialApoyoFormValue,publicado:boolean){const url=apiUrl(`/admin/soluciones/${id}/materiales`);if(v.origen==='archivo'&&v.archivo){const f=crearFormDataMaterial(v.nombre,v.tipo,v.archivo);f.append('publicado',String(publicado));return this.http.post<MaterialSolucion>(url+'/archivo',f).pipe(this.error());}return this.http.post<MaterialSolucion>(url,{nombre:v.nombre,tipo:v.tipo,url:v.url,publicado}).pipe(this.error());}
 publicar(id:number,m:MaterialSolucion){return this.http.patch<MaterialSolucion>(apiUrl(`/admin/soluciones/${id}/materiales/${m.id}/publicacion`),{publicado:!m.publicado}).pipe(this.error());}
 eliminar(id:number,m:MaterialSolucion){return this.http.delete<void>(apiUrl(`/admin/soluciones/${id}/materiales/${m.id}`)).pipe(this.error());}
 descargar(id:number,m:MaterialSolucion){return this.http.get(apiUrl(`/admin/soluciones/${id}/materiales/${m.id}/archivo`),{responseType:'blob'}).pipe(map(blob=>iniciarDescargaBlob(blob,m.nombre)),this.error());}
 enlace(url:string):string|null{try{const u=new URL(url);return ['http:','https:'].includes(u.protocol)&&!u.username&&!u.password?u.href:null;}catch{return null;}}
}

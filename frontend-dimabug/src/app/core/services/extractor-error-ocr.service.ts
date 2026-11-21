import { Injectable } from '@angular/core';
import { ExtractorErrorOcr } from '../ocr-compartido/extractor-error';

/** Adaptador Angular: conserva el contrato y utiliza el algoritmo compartido. */
@Injectable({providedIn:'root'})
export class ExtractorErrorOcrService extends ExtractorErrorOcr {}

/** Fixtures sinteticos existentes; salidas de Angular ANTES de la extraccion. */
export const FIXTURES_AUTORIZADOS: Readonly<Record<string, {sha256: string; textoAngularBaseline: string}>> = {
  "error-ambiguo.png": {
    "sha256": "fb1a5fd2c3e01caeea50309ab2074457ab04b34abb4d1e28cde989ed1d05fb82",
    "textoAngularBaseline": "Error OX0000011b"
  },
  "error-borroso.png": {
    "sha256": "9baf0895b111cdf2b1d0dbd2f8ef2308b68f5437acbc70e170fb46e1b5225603",
    "textoAngularBaseline": "Error 0x0000011b"
  },
  "error-legible.png": {
    "sha256": "d0fb7750f4412f83b88cb30603eebc07b15587993035ed4f43ec93e1c70897f7",
    "textoAngularBaseline": "Error 0x0000011b"
  },
  "sin-texto.png": {
    "sha256": "1eb635692cee045e49f3fb045bbe448d1b3f9fe6ffb91ac98db08297c6930f68",
    "textoAngularBaseline": ""
  }
};

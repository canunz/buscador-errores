package cl.casol.backend.conocimiento.domain;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/** Normalización en memoria: no modifica el conocimiento ni elimina códigos técnicos. */
public final class ConsultaError {
    private static final Pattern FECHA = Pattern.compile(
            "(?<![a-z0-9-])(?:19|20)\\d{2}[ _/.-](?:0[1-9]|1[0-2])[ _/.-](?:0[1-9]|[12]\\d|3[01])(?![a-z0-9-])");
    private static final Pattern SUFIJO_ARCHIVO = Pattern.compile("(?<![a-z0-9])([a-z][a-z0-9-]*)_(\\d+)(\\.[a-z]{1,8})(?=$|[^a-z0-9])");
    private static final Set<String> PREFIJOS_CODIGO = Set.of("ora","error","err","http","sqlstate","win32");
    private static final Pattern TERMINO = Pattern.compile("0x[0-9a-f]+|[a-z]{2,12}-\\d+[a-z0-9]*|[a-z0-9]+");
    private static final Set<String> VACIAS = Set.of("al","el","la","los","las","de","del","en","un","una",
            "y","o","que","para","por","con","como","mi","me","se","es","este","esta","esto",
            "tengo","dice","aparece","sale","muestra","ocurre","puedo","puede","favor","ayuda");
    private static final Set<String> GENERICAS = Set.of("error","archivo","problema","mensaje","txt","log","no","sin","nunca");
    private final String original;
    private final String firma;
    private final List<String> terminos;
    private final List<String> codigos;
    private final List<String> mensaje;
    private final Map<String, Pattern> limites;

    public ConsultaError(String texto) {
        original = plano(texto);
        var palabras = palabras(texto);
        firma = String.join(" ", palabras);
        terminos = List.copyOf(new LinkedHashSet<>(palabras));
        codigos = terminos.stream().filter(t -> t.chars().anyMatch(Character::isDigit)).toList();
        limites = terminos.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(t -> t,
                t -> Pattern.compile("(?<![a-z0-9])" + Pattern.quote(t) + "(?![a-z0-9])")));
        int ruta = original.indexOf('/');
        mensaje = ruta > 0 ? palabras(original.substring(0, ruta)).stream().distinct().filter(terminos::contains).toList() : List.of();
    }

    // Devuelve plano.
    public static String plano(String valor) {
        return Normalizer.normalize(valor == null ? "" : valor, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT)
                .replace('\\', '/').replace('–', '-').replace('—', '-')
                .replaceAll("\\s+", " ").strip();
    }

    // Devuelve palabras.
    private static List<String> palabras(String texto) {
        String normal = SUFIJO_ARCHIVO.matcher(FECHA.matcher(plano(texto)).replaceAll(" "))
                .replaceAll(m -> PREFIJOS_CODIGO.contains(m.group(1)) ? m.group() : m.group(1) + m.group(3));
        var matcher = TERMINO.matcher(normal);
        List<String> resultado = new ArrayList<>();
        while (matcher.find()) {
            String termino = matcher.group();
            if (!VACIAS.contains(termino) && (termino.length() >= 2 || Character.isDigit(termino.charAt(0))))
                resultado.add(termino);
        }
        return resultado;
    }

    public String original() { return original; }
    public String firma() { return firma; }
    public List<String> terminos() { return terminos; }
    public List<String> codigos() { return codigos; }
    // Devuelve peso.
    public int peso(String termino) { return GENERICAS.contains(termino) ? 1 : codigos.contains(termino) ? 6 : 3; }

    /** Solo se puntúan los tres campos autorizados por el buscador público. */
    public double puntuar(String tituloConocimiento, String tituloSolucion, String descripcionSolucion) {
        if (terminos.isEmpty()) return 0;
        String titulo = String.join(" ", palabras(tituloConocimiento));
        String solucion = String.join(" ", palabras(tituloSolucion));
        String descripcion = String.join(" ", palabras(descripcionSolucion));
        String conjunto = titulo + " " + solucion + " " + descripcion;
        // Una ruta parecida no basta si el mensaje describe otra operación (leer frente a escribir).
        if (mensaje.stream().anyMatch(t -> !GENERICAS.contains(t))) {
            double totalMensaje = mensaje.stream().mapToInt(this::peso).sum();
            double encontrado = mensaje.stream().filter(t -> contiene(conjunto, t)).mapToInt(this::peso).sum();
            if (encontrado / totalMensaje < .60) return 0;
        }
        // Una coincidencia parcial nunca sustituye un código concreto por uno distinto.
        if (codigos.stream().anyMatch(c -> !limites.get(c).matcher(conjunto).find())) return 0;
        double total = 0, coincidente = 0, puntos = 0;
        for (String termino : terminos) {
            int peso = peso(termino); total += peso;
            boolean a = contiene(titulo, termino), b = contiene(solucion, termino), c = contiene(descripcion, termino);
            if (a || b || c) coincidente += peso;
            puntos += peso * ((a ? 6 : 0) + (b ? 4 : 0) + (c ? 1 : 0));
        }
        double cobertura = coincidente / total;
        if (cobertura < .60) return 0;
        if (original.equals(plano(tituloConocimiento))) puntos += 1000;
        else if (firma.equals(titulo)) puntos += 600;
        else if (titulo.contains(firma)) puntos += 200;
        if (original.equals(plano(tituloSolucion))) puntos += 500;
        else if (firma.equals(solucion)) puntos += 300;
        else if (solucion.contains(firma)) puntos += 100;
        return puntos + cobertura * 100;
    }

    // Indica si contiene.
    private boolean contiene(String campo, String termino) {
        if (termino.length() >= 4 && !codigos.contains(termino)) return campo.contains(termino);
        return limites.get(termino).matcher(campo).find();
    }
}

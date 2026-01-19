package org.example;

/**
 * Corrección manual de una fila para un usuario y fecha concretos.
 * Se usa en ExceptionsEditorView y luego en ExceptionApplier.
 */
public class ExceptionFix {

    private final String usuario;
    private final String fecha;       // "yyyy-MM-dd"
    private final String turno;       // "A", "B" o "-" (en casos especiales)
    private final String entrada;     // "HH:mm" o "HH:mm:ss" ya normalizado
    private final String salida;      // idem
    private final String descripcion;

    public ExceptionFix(String usuario,
                        String fecha,
                        String turno,
                        String entrada,
                        String salida,
                        String descripcion) {
        this.usuario = usuario;
        this.fecha = fecha;
        this.turno = turno;
        this.entrada = entrada;
        this.salida = salida;
        this.descripcion = descripcion;
    }

    public String getUsuario() {
        return usuario;
    }

    public String getFecha() {
        return fecha;
    }

    public String getTurno() {
        return turno;
    }

    public String getEntrada() {
        return entrada;
    }

    public String getSalida() {
        return salida;
    }

    public String getDescripcion() {
        return descripcion;
    }

    @Override
    public String toString() {
        return "ExceptionFix{" +
                "usuario='" + usuario + '\'' +
                ", fecha='" + fecha + '\'' +
                ", turno='" + turno + '\'' +
                ", entrada='" + entrada + '\'' +
                ", salida='" + salida + '\'' +
                ", descripcion='" + descripcion + '\'' +
                '}';
    }
}

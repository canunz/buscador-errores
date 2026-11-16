package cl.casol.backend.procedimiento.infrastructure.web.dto;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import cl.casol.backend.procedimiento.domain.EstadoProcedimiento;

/** Distingue hardware omitido (cliente anterior) de null explícito (Sin hardware). */
public class GuardarProcedimientoRequest {
    @JsonProperty @NotBlank(message="El nombre es obligatorio") @Size(max=150,message="El nombre no puede superar 150 caracteres") private String nombre;
    @JsonProperty @Size(max=20000) private String descripcion;
    @JsonProperty @Positive private Integer sistemaId;
    @JsonProperty @Positive private Integer moduloId;
    @JsonProperty @Size(max=10000) private String informacionComplementaria;
    @JsonProperty private EstadoProcedimiento estado;
    @Positive private Integer hardwareId;
    private boolean hardwareProporcionado;
    public GuardarProcedimientoRequest(){}
    public GuardarProcedimientoRequest(String nombre,String descripcion){this.nombre=nombre;this.descripcion=descripcion;}
    @JsonSetter(value="hardwareId",nulls=com.fasterxml.jackson.annotation.Nulls.SET) public void asignarHardware(Integer id){hardwareId=id;hardwareProporcionado=true;}
    public String nombre(){return nombre;}public String descripcion(){return descripcion;}public Integer sistemaId(){return sistemaId;}public Integer moduloId(){return moduloId;}public String informacionComplementaria(){return informacionComplementaria;}public EstadoProcedimiento estado(){return estado;}public Integer hardwareId(){return hardwareId;}public boolean hardwareProporcionado(){return hardwareProporcionado;}
}

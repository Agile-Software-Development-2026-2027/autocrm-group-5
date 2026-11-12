package it.devmethodologies.meetingingestor.model;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum JobStatus {
    PROCESSING,
    DONE,
    FAILED;
    @JsonValue
    public String toJson(){
        return name().toLowerCase(Locale.ROOT);
    }
}

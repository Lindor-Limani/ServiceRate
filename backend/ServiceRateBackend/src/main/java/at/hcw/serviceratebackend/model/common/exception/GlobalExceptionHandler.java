package at.hcw.serviceratebackend.model.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Liefert für alle Fehler eine einheitliche JSON-Antwort: { "error": "..." }
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public record ErrorResponse(String error) {}

    // Ungültige Eingaben (z.B. "Ungültige Postleitzahl", "Buchung nicht akzeptiert") -> 400
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleIllegalArgument(IllegalArgumentException ex) {
        return new ErrorResponse(ex.getMessage());
    }

    // Bean-Validation (@Valid) -> 400 mit der ersten Fehlermeldung
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Ungültige Eingabe");
        return new ErrorResponse(message);
    }

    // Übrige fachliche Laufzeitfehler (z.B. "nicht gefunden") -> 400
    // Achtung: NullPointerException ist auch eine RuntimeException und landet hier!
    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleRuntime(RuntimeException ex) {
        // Stacktrace in die Server-Konsole, damit wir bei NPEs & Co. nicht mehr blind sind
        log.error("RuntimeException im Request abgefangen", ex);
        return new ErrorResponse(ex.getMessage());
    }

    // Alles Unerwartete -> 500
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleGeneric(Exception ex) {
        // Vollständigen Stacktrace loggen, BEVOR wir antworten -> wir sehen was wirklich abstürzt
        log.error("Unerwarteter Serverfehler", ex);
        return new ErrorResponse("Interner Serverfehler");
    }
}

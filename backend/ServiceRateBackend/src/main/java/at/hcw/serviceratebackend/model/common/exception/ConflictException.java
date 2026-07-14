package at.hcw.serviceratebackend.model.common.exception;

public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}

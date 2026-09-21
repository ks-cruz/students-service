package co.edu.uptc.students.dto;

public record StudentResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        Long programId) {
}
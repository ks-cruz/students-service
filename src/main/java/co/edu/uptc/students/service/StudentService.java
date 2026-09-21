package co.edu.uptc.students.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.uptc.students.dto.PageResponse;
import co.edu.uptc.students.dto.StudentRequest;
import co.edu.uptc.students.dto.StudentResponse;
import co.edu.uptc.students.entity.Program;
import co.edu.uptc.students.entity.Student;
import co.edu.uptc.students.exception.DuplicateResourceException;
import co.edu.uptc.students.exception.ResourceNotFoundException;
import co.edu.uptc.students.repository.ProgramRepository;
import co.edu.uptc.students.repository.StudentRepository;
import jakarta.persistence.criteria.Predicate;

@Service
public class StudentService {

    private final StudentRepository studentRepository;
    private final ProgramRepository programRepository;

    public StudentService(StudentRepository studentRepository, ProgramRepository programRepository) {
        this.studentRepository = studentRepository;
        this.programRepository = programRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<StudentResponse> getAll(String firstName, String lastName, Long programId, String email,
            int pageNumber, int pageSize, String sortBy, String sortDirection) {

        Pageable pageable = PageRequest.of(pageNumber, pageSize, buildSort(sortBy, sortDirection));
        Specification<Student> specification = buildSpecification(firstName, lastName, programId, email);

        Page<Student> page = studentRepository.findAll(specification, pageable);

        List<StudentResponse> content = page.getContent().stream()
                .map(this::toResponse)
                .toList();

        return new PageResponse<>(content, page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages());
    }

    @Transactional(readOnly = true)
    public StudentResponse getById(Long id) {
        return toResponse(findStudentOrThrow(id));
    }

    @Transactional
    public StudentResponse create(StudentRequest request) {
        if (studentRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("Student with email " + request.email() + " already exists");
        }

        Program program = findProgramOrThrow(request.programId());
        Student student = new Student(null, request.firstName(), request.lastName(), request.email(), program);

        return toResponse(studentRepository.save(student));
    }

    @Transactional
    public StudentResponse update(Long id, StudentRequest request) {
        Student student = findStudentOrThrow(id);

        studentRepository.findByEmail(request.email()).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw new DuplicateResourceException("Student with email " + request.email() + " already exists");
            }
        });

        Program program = findProgramOrThrow(request.programId());

        student.setFirstName(request.firstName());
        student.setLastName(request.lastName());
        student.setEmail(request.email());
        student.setProgram(program);

        return toResponse(studentRepository.save(student));
    }

    @Transactional
    public void delete(Long id) {
        studentRepository.delete(findStudentOrThrow(id));
    }

    private Student findStudentOrThrow(Long id) {
        return studentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Student with id " + id + " was not found"));
    }

    private Program findProgramOrThrow(Long programId) {
        return programRepository.findById(programId)
                .orElseThrow(() -> new ResourceNotFoundException("Program with id " + programId + " was not found"));
    }

    private Sort buildSort(String sortBy, String sortDirection) {
        String property = (sortBy == null || sortBy.isBlank()) ? "id" : sortBy;
        Sort.Direction direction = "desc".equalsIgnoreCase(sortDirection) ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(direction, property);
    }

    private Specification<Student> buildSpecification(String firstName, String lastName, Long programId,
            String email) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (firstName != null && !firstName.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("firstName")), "%" + firstName.toLowerCase() + "%"));
            }
            if (lastName != null && !lastName.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("lastName")), "%" + lastName.toLowerCase() + "%"));
            }
            if (programId != null) {
                predicates.add(cb.equal(root.get("program").get("id"), programId));
            }
            if (email != null && !email.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("email")), "%" + email.toLowerCase() + "%"));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private StudentResponse toResponse(Student student) {
        return new StudentResponse(
                student.getId(),
                student.getFirstName(),
                student.getLastName(),
                student.getEmail(),
                student.getProgram().getId());
    }
}
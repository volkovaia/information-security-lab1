package org.example.infseclab1.repository;


import org.example.infseclab1.entity.Note;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoteRepository extends JpaRepository<Note, Long> {}
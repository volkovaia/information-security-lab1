package org.example.infseclab1.controller;


import org.example.infseclab1.dto.NoteRequest;
import org.example.infseclab1.entity.Note;
import org.example.infseclab1.repository.NoteRepository;
import jakarta.validation.Valid;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/data")
public class DataController {

    private final NoteRepository noteRepository;
    // OWASP Sanitizer: вырезаем теги скриптов/html
    private final PolicyFactory sanitizer = new HtmlPolicyBuilder().toFactory();

    public DataController(NoteRepository noteRepository) {
        this.noteRepository = noteRepository;
    }

    @GetMapping
    public List<Note> getAllNotes() {
        return noteRepository.findAll();
    }

    @PostMapping
    public Note createNote(@Valid @RequestBody NoteRequest request) {
        //Защита от XSS: очиcтка пользовательского ввода перед сохранением, возвратом
        String cleanTitle = sanitizer.sanitize(request.title());
        String cleanContent = sanitizer.sanitize(request.content());

        return noteRepository.save(new Note(cleanTitle, cleanContent));
    }
}

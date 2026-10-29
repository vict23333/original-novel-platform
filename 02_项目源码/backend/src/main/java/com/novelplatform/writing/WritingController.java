package com.novelplatform.writing;

import com.novelplatform.ai.AiGateway;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/author/novels/{novel}/writing")
public class WritingController {
    private final WritingService service;
    private final WritingContext context;
    private final WritingAiService ai;
    public WritingController(WritingService service,WritingContext context,WritingAiService ai){this.service=service;this.context=context;this.ai=ai;}
    private long owner(Authentication auth){return Long.parseLong(auth.getName());}
    @GetMapping public WritingService.Workspace get(Authentication a,@PathVariable long novel){return service.workspace(owner(a),novel);}
    @PutMapping public WritingProject save(Authentication a,@PathVariable long novel,@Valid @RequestBody WritingInputs.Project input){return service.save(owner(a),novel,input);}
    @PostMapping("/characters") @ResponseStatus(HttpStatus.CREATED)
    public WritingCharacter create(Authentication a,@PathVariable long novel,@Valid @RequestBody WritingInputs.Character input){return service.saveCharacter(owner(a),novel,null,input);}
    @PutMapping("/characters/{id}") public WritingCharacter update(Authentication a,@PathVariable long novel,@PathVariable long id,@Valid @RequestBody WritingInputs.Character input){return service.saveCharacter(owner(a),novel,id,input);}
    @DeleteMapping("/characters/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication a,@PathVariable long novel,@PathVariable long id,@RequestParam long version){service.deleteCharacter(owner(a),novel,id,version);}
    @GetMapping("/previous") public List<WritingContext.Previous> previous(Authentication a,@PathVariable long novel,@RequestParam int before){return context.previous(owner(a),novel,before);}
    @PostMapping("/context") public WritingContext.Preview preview(Authentication a,@PathVariable long novel,@Valid @RequestBody WritingInputs.Task input){return context.build(owner(a),novel,input);}
    @PostMapping("/generate") public AiGateway.Result generate(Authentication a,@PathVariable long novel,@Valid @RequestBody WritingInputs.Task input){return ai.generate(owner(a),novel,input);}
}

package org.lolaf.staffix.admin.server.engines;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/engines")
public class EnginesController {

    @GetMapping
    public List<Engine> listEngines() {
        return List.of();
    }
}

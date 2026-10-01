package org.lolaf.staffix.admin.server.engines;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/engines")
@RequiredArgsConstructor
public class EnginesController {

    private final EngineRegistry registry;

    @GetMapping
    public List<Engine> listEngines() {
        return registry.list();
    }
}

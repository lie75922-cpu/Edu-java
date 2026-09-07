package com.smartlearning.research;

import com.smartlearning.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/research")
public class ResearchExerciseController {

    private final ResearchExerciseCatalogService catalogService;

    ResearchExerciseController(ResearchExerciseCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/exercises")
    public ApiResponse<ResearchExercisePage> exercises(
            @RequestParam(required = false) String topic,
            @RequestParam(required = false) String area,
            @RequestParam(required = false, name = "q") String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(catalogService.search(topic, area, query, page, size));
    }
}


package com.abrar.shopsphere.ai.controller;

import com.abrar.shopsphere.ai.dto.PersonalizedRecommendationDTO;
import com.abrar.shopsphere.ai.service.PersonalizedRecommendationService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/ai/recommendations")
public class PersonalizedRecommendationController {

    private final PersonalizedRecommendationService recommendationService;

    public PersonalizedRecommendationController(
            PersonalizedRecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping("/personalized")
    public List<PersonalizedRecommendationDTO> getPersonalizedRecommendations(
            @RequestParam(defaultValue = "8") int limit) {

        return recommendationService.getRecommendations(limit);
    }
}

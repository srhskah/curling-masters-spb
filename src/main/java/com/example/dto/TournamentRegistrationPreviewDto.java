package com.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentRegistrationPreviewDto {
    private List<String> mainDirectUsernames;
    private List<String> qualifierSeedUsernames;
    private String modeDescription;
}

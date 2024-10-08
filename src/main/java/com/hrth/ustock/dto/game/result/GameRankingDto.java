package com.hrth.ustock.dto.game.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameRankingDto {
    private long userId;
    private String nickname;
    private long total;
    private double profitRate;
}

package com.hrth.ustock.dto.game.result;

import com.hrth.ustock.entity.game.PlayerType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameResultResponseDto {
    private int year;
    private String nickname;
    private long total;
    private double profitRate;
    private PlayerType playerType;
}

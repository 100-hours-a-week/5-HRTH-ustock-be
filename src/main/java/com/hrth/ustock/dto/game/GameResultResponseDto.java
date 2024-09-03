package com.hrth.ustock.dto.game;

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
    private String nickname;
    private long budget;
    private PlayerType playerType;
}

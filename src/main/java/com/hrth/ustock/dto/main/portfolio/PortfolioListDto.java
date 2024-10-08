package com.hrth.ustock.dto.main.portfolio;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PortfolioListDto {
    private long budget;
    private long principal;
    private long profit;
    private double profitRate;
    private List<PortfolioEmbedDto> list = new ArrayList<>();
}

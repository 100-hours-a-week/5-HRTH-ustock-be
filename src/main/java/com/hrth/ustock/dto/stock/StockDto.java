package com.hrth.ustock.dto.stock;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockDto {
    private String code;
    private String name;
    private int price;
    private double changeRate;
    private String logo;
}

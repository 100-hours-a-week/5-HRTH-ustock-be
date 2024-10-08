package com.hrth.ustock.entity.game;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Entity
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "game_stock_yearly")
public class GameStockYearly {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "yearly_id")
    private Long id;

    private int price;

    private int year;

    private String date;

    @ManyToOne
    @JoinColumn(name = "stock_id")
    private GameStockInfo gameStockInfo;

    @OneToMany(mappedBy = "gameStockYearly", fetch = FetchType.LAZY)
    private List<GameHint> gameHints;

    @OneToOne(mappedBy = "gameStockYearly", fetch = FetchType.LAZY)
    private GameNews gameNews;
}

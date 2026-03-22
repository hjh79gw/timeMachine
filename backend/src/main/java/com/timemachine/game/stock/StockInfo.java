package com.timemachine.game.stock;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "game_stock_info")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class StockInfo {

    @Id
    @Column(length = 20)
    private String symbol;

    @Column(length = 100)
    private String name;

    @Column(length = 10)
    private String market;

    @Column(length = 100)
    private String sector;
}

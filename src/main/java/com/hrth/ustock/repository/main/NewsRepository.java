package com.hrth.ustock.repository.main;

import com.hrth.ustock.entity.main.News;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NewsRepository extends JpaRepository<News, Long> {

    List<News> findAllByStockCodeAndDateBetween(String code, String start, String end);

    List<News> findTop15ByStockCodeOrderByDateDesc(String code);
}

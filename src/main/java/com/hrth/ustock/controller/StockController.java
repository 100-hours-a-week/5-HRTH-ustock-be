package com.hrth.ustock.controller;

import com.hrth.ustock.dto.chart.ChartResponseDto;
import com.hrth.ustock.dto.stock.StockDto;
import com.hrth.ustock.dto.stock.StockResponseDto;
import com.hrth.ustock.exception.ChartNotFoundException;
import com.hrth.ustock.exception.StockNotFoundException;
import com.hrth.ustock.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/stocks")
public class StockController {
    private final StockService stockService;

    private static final String DATE_PATTERN = "^[0-9]{4}/[0-9]{2}/[0-9]{2}$";
    private static final Pattern pattern = Pattern.compile(DATE_PATTERN);

    // 4. 오늘의 증시 정보 조회
    @GetMapping("/market")
    public ResponseEntity<?> marketInformation() {
        Map<String, Object> marketInfo;
        try {
            marketInfo = stockService.getMarketInfo();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("한국투자 API 조회 실패");
        }

        return ResponseEntity.ok(marketInfo);
    }

    // 5. 정렬 기준을 바탕으로 종목 리스트 조회
    @GetMapping
    public ResponseEntity<?> stockList(@RequestParam String order) {
        Map<String, List<StockResponseDto>> stockMap;
        try {
            stockMap = stockService.getStockList(order);
        } catch (StockNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("주식 목록을 찾을 수 없습니다.");
        }

        if (stockMap == null)
            return ResponseEntity.badRequest().body("잘못된 정렬 기준입니다.");

        return ResponseEntity.ok(stockMap);
    }

    // 6. 종목 검색
    @GetMapping("/search")
    public ResponseEntity<?> searchStock(@RequestParam String query) {

        if (query.length() > 10) {
            return ResponseEntity.badRequest().build();
        }

        try {
            List<StockDto> stockList = stockService.findByStockName(query);
            return ResponseEntity.ok(stockList);
        } catch (StockNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // 14. 주식 상세정보 조회
    @GetMapping("/{code}")
    public ResponseEntity<?> findStockByCode(@PathVariable String code) {
        StockResponseDto stockResponseDto;
        try {
            stockResponseDto = stockService.getStockInfo(code);
        } catch (ChartNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("차트 정보를 조회할 수 없습니다.");
        }

        return ResponseEntity.ok(stockResponseDto);
    }

    // 15. 종목 차트 조회
    @GetMapping("/{code}/chart")
    public ResponseEntity<?> getStockChart(
            @PathVariable String code, @RequestParam int period, @RequestParam String start, @RequestParam String end) {

        if (!pattern.matcher(start).matches() || !pattern.matcher(end).matches() ||
                period < 1 || period > 4 || code.length() != 6) {
            return ResponseEntity.badRequest().build();
        }

        List<ChartResponseDto> list = stockService.getStockChartAndNews(code, period, start, end);
        return ResponseEntity.ok(list);
    }
}

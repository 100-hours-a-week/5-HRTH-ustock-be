package com.hrth.ustock.controller;

import com.hrth.ustock.service.cron.StockCronService;
import io.sentry.Sentry;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/scheduler")
@EnableScheduling
public class CronController {
    private final StockCronService stockCronService;

    // 주중 오전 9시에 시작해서 30분마다 실행하고 오후 15시 30분에 끝남
    @Scheduled(cron = "0 0/30 9-15 ? * MON-FRI")
    public void setChartData() {
        try {
            stockCronService.saveMarketData();
        } catch (Exception e) {
            Sentry.captureException(e);
        }
    }

    // 화-토 오전 9시 (setChartData와 겹치지 않게 10분 일찍 수행)에 전일(수정주가) 차트 데이터 mysql에 저장, redis 차트 데이터 정리
    @Scheduled(cron = "0 50 08 ? * TUE-SAT")
    public void setEditedChartData() {
        try {
            stockCronService.saveEditedChartData();
        } catch (Exception e) {
            Sentry.captureException(e);
        }
    }

    // 주중 오전 9시에 시작해서 30분마다 실행하고 오후 15시 30분에 끝남
    @GetMapping("/test/1")
    public void testChartData() {
        try {
            stockCronService.saveMarketData();
        } catch (Exception e) {
            Sentry.captureException(e);
        }
    }

    // 화-금 오전 9시에 전일(수정주가) 차트 데이터 저장
    @GetMapping("/test/3")
    public void testEditedChartData() {
        try {
            stockCronService.saveEditedChartData();
        } catch (Exception e) {
            Sentry.captureException(e);
        }
    }
}

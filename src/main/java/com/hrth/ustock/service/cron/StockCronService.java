package com.hrth.ustock.service.cron;

import com.hrth.ustock.dto.main.stock.MarketResponseDto;
import com.hrth.ustock.dto.main.stock.StockResponseDto;
import com.hrth.ustock.entity.main.Chart;
import com.hrth.ustock.entity.main.Stock;
import com.hrth.ustock.exception.domain.stock.StockException;
import com.hrth.ustock.exception.kisapi.KisApiException;
import com.hrth.ustock.repository.main.ChartBatchRepository;
import com.hrth.ustock.repository.main.StockRepository;
import com.hrth.ustock.util.DateConverter;
import com.hrth.ustock.util.KisApiAuthManager;
import com.hrth.ustock.util.RedisJsonManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hrth.ustock.exception.domain.stock.StockExceptionType.MARKET_NOT_FOUND;
import static com.hrth.ustock.exception.domain.stock.StockExceptionType.STOCK_REQUEST_FAILED;
import static com.hrth.ustock.exception.kisapi.KisApiExceptionType.API_REQUEST_FAILED;
import static com.hrth.ustock.service.main.StockServiceConst.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockCronService {

    private static final int TOP_RANK_RANGE = 5;

    private final StockRepository stockRepository;
    private final ChartBatchRepository chartBatchRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final KisApiAuthManager authManager;
    private final DateConverter dateConverter;

    private static final DateTimeFormatter requestFormatter = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter redisFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd/HH/mm");
    private static final DateTimeFormatter mysqlFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private final RedisJsonManager redisJsonManager;

    // 종목별 현재가, 당일 차트 데이터 redis에 갱신
    public void saveStockData() {
        log.info("현재가 크론잡 시작");
        String startDate = ZonedDateTime.now(ZoneId.of("Asia/Seoul")).format(requestFormatter);
        String redisDate = dateConverter.minuteFormatter(0);
        String chartDate = ZonedDateTime.now(ZoneId.of("Asia/Seoul")).format(mysqlFormatter);
        List<Stock> allStocks = stockRepository.findAll();

        for (Stock stock : allStocks) {
            String code = stock.getCode();
            String queryParams = "?fid_cond_mrkt_div_code=J" +
                    "&fid_input_iscd=" + code +
                    "&fid_input_date_1=" + startDate +
                    "&fid_input_date_2=" + startDate +
                    "&fid_period_div_code=D" +
                    "&fid_org_adj_prc=0";
            String apiUri = "/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice";

            Map response = authManager.getCronData(apiUri, queryParams, "FHKST03010100");

            if (response == null) {
                continue;
            }

            Map<String, String> output1 = (Map<String, String>) response.get("output1");

            if (output1 == null || output1.isEmpty()) {
                log.info("saveStockChartData: api request failed, code: {}", code);
                continue;
            }

            String current = output1.get(STOCK_CURRENT_PRICE);
            String change = output1.get(CHANGE_FROM_PREVIOUS_STOCK);
            String changeRate = output1.get(CHANGE_RATE_FROM_PREVIOUS_STOCK);

            redisTemplate.opsForHash().put(code, REDIS_CURRENT_KEY, current);
            redisTemplate.opsForHash().put(code, REDIS_CHANGE_KEY, change);
            redisTemplate.opsForHash().put(code, REDIS_CHANGE_RATE_KEY, changeRate);
            redisTemplate.opsForHash().put(code, REDIS_DATE_KEY, redisDate);

            List<Map<String, String>> output2 = (List<Map<String, String>>) response.get("output2");
            // 분봉
            String high = output1.get(STOCK_MARKET_HIGH);
            String low = output1.get(STOCK_MARKET_LOW);
            String open = output1.get(STOCK_MARKET_OPEN);
            // 장 마감 전까진 종가가 없으니 현재가로 저장
            String close = output1.get(STOCK_MARKET_CLOSE);

            Map<String, Object> chart = new HashMap<>();
            chart.put(REDIS_CHART_HIGH_KEY, high);
            chart.put(REDIS_CHART_LOW_KEY, low);
            chart.put(REDIS_CHART_OPEN_KEY, open);
            chart.put(REDIS_CHART_CLOSE_KEY, close);
            chart.put(REDIS_DATE_KEY, chartDate);

            String jsonString = redisJsonManager.mapStringConvert(chart);
            redisTemplate.opsForHash().put(code, REDIS_CHART_KEY, jsonString);
        }
        log.info("현재가 크론잡 종료");
    }

    public void saveMarketData() {
        Map<String, String> kospi = requestMarketInfo(KOSPI_CODE);
        Map<String, String> kosdaq = requestMarketInfo(KOSDAQ_CODE);

        String redisDate = ZonedDateTime.now(ZoneId.of("Asia/Seoul")).format(redisFormatter);

        if (kospi == null || kosdaq == null) {
            throw new StockException(MARKET_NOT_FOUND);
        }

        Map<String, Object> marketInfo = new HashMap<>();
        marketInfo.put("kospi", makeMarketDto(kospi));
        marketInfo.put("kosdaq", makeMarketDto(kosdaq));
        marketInfo.put("date", redisDate);

        String jsonString = redisJsonManager.mapStringConvert(marketInfo);
        redisTemplate.opsForValue().set("market_info", jsonString);
    }

    private Map<String, String> requestMarketInfo(String marketCode) {
        String queryParams = "?fid_cond_mrkt_div_code=U"
                + "&fid_input_iscd=" + marketCode;
        String apiUri = "/uapi/domestic-stock/v1/quotations/inquire-index-price";

        Map response = authManager.getCronData(apiUri, queryParams, "FHPUP02100000");

        if (response == null) {
            throw new KisApiException(API_REQUEST_FAILED);
        }

        if (response.get("output") == null || response.get("output").equals("")) {
            log.info("requestMarketInfo: api request failed, code: {}", marketCode);
            throw new KisApiException(API_REQUEST_FAILED);
        }

        return (Map<String, String>) response.get("output");
    }

    private MarketResponseDto makeMarketDto(Map<String, String> responseMap) {
        double price = Double.parseDouble(responseMap.get(MARKET_CURRENT_PRICE));
        double change = Double.parseDouble(responseMap.get(CHANGE_FROM_PREVIOUS_MARKET));
        double changeRate = Double.parseDouble(responseMap.get(CHANGE_RATE_FROM_PREVIOUS_MARKET));

        return MarketResponseDto.builder()
                .price(price)
                .change(change)
                .changeRate(changeRate)
                .build();
    }

    @Transactional
    public void saveEditedChartData() {
        log.info("차트 크론잡 시작");
        String startDate = ZonedDateTime.now(ZoneId.of("Asia/Seoul")).minusDays(1).format(requestFormatter);
        String mysqlDate = ZonedDateTime.now(ZoneId.of("Asia/Seoul")).minusDays(1).format(mysqlFormatter);

        List<Stock> allStocks = stockRepository.findAll();
        List<Chart> chartList = new ArrayList<>();
        for (Stock stock : allStocks) {
            String code = stock.getCode();
            // 장이 열리면 전날 redis_chart 삭제
            redisTemplate.opsForHash().delete(code, REDIS_CHART_KEY);
            String queryParams = "?fid_cond_mrkt_div_code=J" +
                    "&fid_input_iscd=" + code +
                    "&fid_input_date_1=" + startDate +
                    "&fid_input_date_2=" + startDate +
                    "&fid_period_div_code=D" +
                    "&fid_org_adj_prc=0";
            String apiUri = "/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice";

            Map response = authManager.getCronData(apiUri, queryParams, "FHKST03010100");

            if (response == null) continue;

            List<Map<String, String>> output2 = (List<Map<String, String>>) response.get("output2");

            if (output2 == null || output2.isEmpty() || output2.get(0).isEmpty()) {
                continue;
            }

            String high = output2.get(0).get(STOCK_HIGH);
            String low = output2.get(0).get(STOCK_LOW);
            String open = output2.get(0).get(STOCK_OPEN);
            String close = output2.get(0).get(STOCK_CLOSE);

            Chart chart = Chart.builder()
                    .stock(stock)
                    .high(Integer.parseInt(high))
                    .low(Integer.parseInt(low))
                    .open(Integer.parseInt(open))
                    .close(Integer.parseInt(close))
                    .date(mysqlDate)
                    .build();

            chartList.add(chart);
        }
        chartBatchRepository.batchInsert(chartList);
        log.info("차트 크론잡 종료");
    }

    public void saveRankData(int addMinute) {
        log.info("랭킹 크론잡 시작");
        saveTopRank(addMinute);
        saveTradeRank(addMinute);
        saveCapitalRank(addMinute);
        saveChangeRank(addMinute);
        log.info("랭킹 크론잡 종료");
    }

    public List<StockResponseDto> saveTopRank(int addMinute) {
        String queryParams = "?fid_cond_mrkt_div_code=J" +
                "&fid_cond_scr_div_code=20171" +
                "&fid_div_cls_code=1" +
                "&fid_input_iscd=0000" +
                "&fid_trgt_cls_code=0" +
                "&fid_blng_cls_code=0" +
                "&fid_trgt_cls_code=111111111" +
                "&fid_trgt_exls_cls_code=0111001101" +
                "&fid_input_price_1=" +
                "&fid_input_price_2=" +
                "&fid_vol_cnt=" +
                "&fid_input_date_1=";

        String apiUri = "/uapi/domestic-stock/v1/quotations/volume-rank";
        Map response = authManager.getCronData(apiUri, queryParams, "FHPST01710000");
        return saveToRedis(response, "top", addMinute);
    }

    public List<StockResponseDto> saveTradeRank(int addMinute) {
        String queryParams = "?fid_cond_mrkt_div_code=J" +
                "&fid_cond_scr_div_code=20171" +
                "&fid_div_cls_code=1" +
                "&fid_input_iscd=0000" +
                "&fid_trgt_cls_code=0" +
                "&fid_blng_cls_code=0" +
                "&fid_trgt_cls_code=111111111" +
                "&fid_trgt_exls_cls_code=0111001101" +
                "&fid_input_price_1=" +
                "&fid_input_price_2=" +
                "&fid_vol_cnt=" +
                "&fid_input_date_1=";

        String apiUri = "/uapi/domestic-stock/v1/quotations/volume-rank";
        Map response = authManager.getCronData(apiUri, queryParams, "FHPST01710000");
        return saveToRedis(response, "trade", addMinute);
    }

    public List<StockResponseDto> saveCapitalRank(int addMinute) {
        String queryParams = "?fid_cond_mrkt_div_code=J" +
                "&fid_cond_scr_div_code=20174" +
                "&fid_div_cls_code=1" +
                "&fid_input_iscd=0000" +
                "&fid_trgt_cls_code=0" +
                "&fid_trgt_exls_cls_code=0" +
                "&fid_input_price_1=" +
                "&fid_input_price_2=" +
                "&fid_vol_cnt=";

        String apiUri = "/uapi/domestic-stock/v1/ranking/market-cap";
        Map response = authManager.getCronData(apiUri, queryParams, "FHPST01740000");
        return saveToRedis(response, "capital", addMinute);
    }

    public List<StockResponseDto> saveChangeRank(int addMinute) {
        String queryParams = "?fid_cond_mrkt_div_code=J" +
                "&fid_cond_scr_div_code=20170" +
                "&fid_input_iscd=0000" +
                "&fid_rank_sort_cls_code=0" +
                "&fid_input_cnt_1=0" +
                "&fid_prc_cls_code=1" +
                "&fid_trgt_cls_code=0" +
                "&fid_trgt_exls_cls_code=0" +
                "&fid_div_cls_code=0" +
                "&fid_input_price_1=" +
                "&fid_input_price_2=" +
                "&fid_rsfl_rate1=" +
                "&fid_rsfl_rate2=" +
                "&fid_vol_cnt=";

        String apiUri = "/uapi/domestic-stock/v1/ranking/fluctuation";
        Map response = authManager.getCronData(apiUri, queryParams, "FHPST01700000");
        return saveToRedis(response, "change", addMinute);
    }

    private List<StockResponseDto> saveToRedis(Map response, String redis_key, int addMinute) {
        String redisDate = dateConverter.minuteFormatter(addMinute);
        if (response == null || response.get("output") == null || response.get("output").equals(""))
            throw new StockException(STOCK_REQUEST_FAILED);

        List<Map<String, String>> output = (List<Map<String, String>>) response.get("output");

        List<StockResponseDto> stockList = makeStockResponseDto(output, redis_key);

        String dtoString = redisJsonManager.serializeList(stockList);
        redisTemplate.opsForValue().set("ranking_" + redis_key + "_" + redisDate, dtoString);

        int fortyMinute = 40 * 60;
        redisTemplate.expire("ranking_" + redis_key + "_" + redisDate, fortyMinute, TimeUnit.SECONDS);

        return stockList;
    }


    private List<StockResponseDto> makeStockResponseDto(List<Map<String, String>> responseList, String order) {
        int range = "top".equals(order) ? TOP_RANK_RANGE : responseList.size();
        String stockCodeKey = "change".equals(order) ? CHANGE_STOCK_CODE : STOCK_CODE;

        List<StockResponseDto> stockList = new ArrayList<>();
        for (int i = 0; i < range; i++) {
            Map<String, String> responseMap = responseList.get(i);
            stockList.add(StockResponseDto.builder()
                    .name(responseMap.get(STOCK_NAME))
                    .code(responseMap.get(stockCodeKey))
                    .price(Integer.parseInt(responseMap.get(STOCK_CURRENT_PRICE)))
                    .change(Integer.parseInt(responseMap.get(CHANGE_FROM_PREVIOUS_STOCK)))
                    .changeRate(Double.parseDouble(responseMap.get(CHANGE_RATE_FROM_PREVIOUS_STOCK)))
                    .build()
            );
        }

        List<String> codeList = stockList.stream()
                .map(StockResponseDto::getCode)
                .toList();

        List<Stock> findStockList = stockRepository.findAllByCodeIn(codeList);
        for (Stock stock : findStockList) {
            for (StockResponseDto dto : stockList) {
                if (dto.getCode().equals(stock.getCode())) {
                    dto.setLogo(stock.getLogo());
                    break;
                }
            }
        }

        for (StockResponseDto stock : stockList) {
            if (!codeList.contains(stock.getCode())) {
                stockRepository.save(
                        Stock.builder()
                                .name(stock.getName())
                                .code(stock.getCode())
                                .build()
                );
            }
        }

        return stockList;
    }
}

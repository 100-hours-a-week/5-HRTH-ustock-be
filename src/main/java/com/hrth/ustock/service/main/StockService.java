package com.hrth.ustock.service.main;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrth.ustock.dto.main.chart.ChartDto;
import com.hrth.ustock.dto.main.chart.ChartResponseDto;
import com.hrth.ustock.dto.main.stock.*;
import com.hrth.ustock.entity.main.Chart;
import com.hrth.ustock.entity.main.News;
import com.hrth.ustock.entity.main.Stock;
import com.hrth.ustock.exception.domain.chart.ChartException;
import com.hrth.ustock.exception.domain.stock.StockException;
import com.hrth.ustock.repository.main.ChartRepository;
import com.hrth.ustock.repository.main.NewsRepository;
import com.hrth.ustock.repository.main.StockRepository;
import com.hrth.ustock.service.cron.StockCronService;
import com.hrth.ustock.util.DateConverter;
import com.hrth.ustock.util.KisApiAuthManager;
import com.hrth.ustock.util.RedisJsonManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static com.hrth.ustock.exception.domain.chart.ChartExceptionType.CHART_NOT_FOUND;
import static com.hrth.ustock.exception.domain.chart.ChartExceptionType.PERIOD_NOT_ALLOWED;
import static com.hrth.ustock.exception.domain.stock.StockExceptionType.*;
import static com.hrth.ustock.service.main.StockServiceConst.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {
    private static final int TOP_RANK_RANGE = 5;

    private final NewsRepository newsRepository;
    private final StockRepository stockRepository;
    private final ChartRepository chartRepository;
    private final StockCronService stockCronService;
    private final RedisTemplate<String, String> redisTemplate;

    private final ObjectMapper objectMapper;
    private final DateConverter dateConverter;
    private final KisApiAuthManager authManager;
    private final RedisJsonManager redisJsonManager;

    @Transactional
    public List<StockResponseDto> searchStock(String query) {
        if (isQueryInvalid(query))
            throw new StockException(STOCK_NOT_FOUND);

        String stockCodeRegex = "^\\d{1,6}$|^Q\\d{1,6}$";

        List<Stock> list;
        if (query.matches(stockCodeRegex)) {
            list = stockRepository.findByCodeStartingWith(query);
            list.addAll(stockRepository.findByCodeContainingButNotStartingWith(query));
        } else {
            list = stockRepository.findByNameStartingWith(query);
            list.addAll(stockRepository.findByNameContainingButNotStartingWith(query));
        }

        if (list.isEmpty())
            throw new StockException(STOCK_NOT_FOUND);

        List<StockResponseDto> stockDtoList = new ArrayList<>();
        for (Stock stock : list) {
            Map<String, String> redisMap = getCurrentChangeChangeRate(stock.getCode());

            if (redisMap == null) continue;

            String currentSaved = redisMap.get(REDIS_CURRENT_KEY);
            String rateSaved = redisMap.get(REDIS_CHANGE_RATE_KEY);

            StockResponseDto stockDto = stock.toDto();
            stockDto.setPrice(Integer.parseInt(currentSaved));
            stockDto.setChangeRate(Double.parseDouble(rateSaved));
            stockDtoList.add(stockDto);
        }

        return stockDtoList;
    }

    private boolean isQueryInvalid(String query) {
        if (query.isBlank()) return true;

        String[] words = query.split("");

        Map<String, Integer> indexMap = new HashMap<>();
        int[] counts = new int[1000];
        int idx = 0;

        for (String word : words) {
            if (!indexMap.containsKey(word))
                indexMap.put(word, idx++);

            int wordIndex = indexMap.get(word);

            if (idx >= counts.length) return true;
            if (++counts[wordIndex] >= 10) return true;
        }

        return false;
    }

    @Transactional
    public StockResponseDto getStockInfo(String code) {
        Stock stock = stockRepository.findByCode(code).orElseThrow(() -> new StockException(STOCK_NOT_FOUND));

        Map<String, String> redisMap = getCurrentChangeChangeRate(stock.getCode());

        if (redisMap == null)
            throw new StockException(CURRENT_NOT_FOUND);

        String currentSaved = redisMap.get(REDIS_CURRENT_KEY);
        String changeSaved = redisMap.get(REDIS_CHANGE_KEY);
        String rateSaved = redisMap.get(REDIS_CHANGE_RATE_KEY);

        return StockResponseDto.builder()
                .code(stock.getCode())
                .name(stock.getName())
                .logo(stock.getLogo())
                .price(Integer.parseInt(currentSaved))
                .change(Integer.parseInt(changeSaved))
                .changeRate(Double.parseDouble(rateSaved))
                .build();
    }

    public List<ChartResponseDto> getStockChartAndNews(String code, int period) {
        // 1: 일봉, 2: 주봉, 3: 월봉
        if (period < 1 || 3 < period)
            throw new ChartException(PERIOD_NOT_ALLOWED);

        String start = dateConverter.getStartDateOneYearAgo();
        String end = dateConverter.getCurrentDate();
        return switch (period) {
            case 1 -> {
                List<ChartResponseDto> list = getChartByRangeList(code, dateConverter.getDailyRanges(start, end), start, end);
                String chart = (String) redisTemplate.opsForHash().get(code, "chart");
                if (chart == null) {
                    yield list;
                }
                Map<String, Object> json = redisJsonManager.stringMapConvert(chart);
                String date = json.get("date").toString();
                int open = Integer.parseInt(json.get("open").toString());
                int close = Integer.parseInt(json.get("close").toString());
                int high = Integer.parseInt(json.get("high").toString());
                int low = Integer.parseInt(json.get("low").toString());
                ChartDto chartData = ChartDto.builder()
                        .high(high)
                        .low(low)
                        .open(open)
                        .close(close)
                        .build();
                ChartResponseDto dto = ChartResponseDto.builder()
                        .candle(chartData)
                        .date(date)
                        .news(new ArrayList<>())
                        .build();
                list.add(dto);
                yield list;
            }
            case 2 -> getChartByRangeList(code, dateConverter.getWeeklyRanges(start, end), start, end);
            case 3 -> getChartByRangeList(code, dateConverter.getMonthlyRanges(start, end), start, end);
            default -> null;
        };
    }

    private List<ChartResponseDto> getChartByRangeList(String code, List<Pair<String, String>> dateList, String start, String end) {
        List<ChartResponseDto> chartListResponse = new ArrayList<>();
        List<Chart> chartList = chartRepository.findAllByStockCodeAndDateBetween(code, start, end);
        List<News> newsList = newsRepository.findAllByStockCodeAndDateBetween(code, start, end);

        if (chartList == null || chartList.isEmpty())
            throw new ChartException(CHART_NOT_FOUND);

        for (Pair<String, String> data : dateList) {
            String startDate = data.getFirst();
            String endDate = data.getSecond();

            ChartResponseDto chartResponseDto = new ChartResponseDto();
            chartResponseDto.setCandle(ChartDto.builder().build());
            chartResponseDto.setNews(new ArrayList<>());

            ChartDto candle = chartResponseDto.getCandle();
            candle.setHigh(Integer.MIN_VALUE);
            candle.setLow(Integer.MAX_VALUE);
            chartResponseDto.setDate(startDate);

            chartList.stream()
                    .filter(chart ->
                    {
                        String date = chart.getDate();
                        return date.compareTo(startDate) >= 0 && date.compareTo(endDate) <= 0;
                    })
                    .forEach(chart -> {
                        if (chart.getDate().equals(startDate)) {
                            candle.setOpen(chart.getOpen());
                        }
                        candle.setClose(chart.getClose());
                        candle.setHigh(Math.max(chart.getHigh(), candle.getHigh()));
                        candle.setLow(Math.min(chart.getLow(), candle.getLow()));
                    });

            if (candle.getHigh() == Integer.MIN_VALUE) continue;

            if (candle.getOpen() == 0)
                candle.setOpen(chartList.get(0).getOpen());

            newsList.stream()
                    .filter(news ->
                    {
                        String date = news.getDate();
                        return date.compareTo(startDate) >= 0 && date.compareTo(endDate) <= 0;
                    })
                    .forEach(news -> chartResponseDto.getNews().add(news.toEmbedDto()));

            chartListResponse.add(chartResponseDto);
        }

        return chartListResponse;
    }

    public AllMarketResponseDto getMarketInfo() {
        String marketInfo = redisTemplate.opsForValue().get("market_info");

        if (marketInfo == null) {
            stockCronService.saveMarketData();
            marketInfo = redisTemplate.opsForValue().get("market_info");
        }

        Map<String, Object> redisResult = redisJsonManager.stringMapConvert(marketInfo);

        MarketResponseDto kospi = objectMapper.convertValue(redisResult.get("kospi"), MarketResponseDto.class);
        MarketResponseDto kosdaq = objectMapper.convertValue(redisResult.get("kosdaq"), MarketResponseDto.class);

        return AllMarketResponseDto.builder()
                .kospi(kospi)
                .kosdaq(kosdaq)
                .build();
    }

    public List<StockResponseDto> getStockList(String order) {
        return switch (order) {
            case "top" -> requestOrderByTop(order);
            case "trade" -> requestOrderByTrade(order);
            case "capital" -> requestOrderByCapital();
            case "change" -> requestOrderByChange();
            default -> throw new StockException(ORDER_NOT_VALID);
        };
    }

    protected Map<String, String> getCurrentChangeChangeRate(String code) {
        String current = (String) redisTemplate.opsForHash().get(code, REDIS_CURRENT_KEY);
        String change = (String) redisTemplate.opsForHash().get(code, REDIS_CHANGE_KEY);
        String changeRate = (String) redisTemplate.opsForHash().get(code, REDIS_CHANGE_RATE_KEY);

        if (current == null || change == null || changeRate == null)
            throw new StockException(CURRENT_NOT_FOUND);

        return Map.of(
                "current", current,
                "change", change,
                "changeRate", changeRate
        );
    }

    private List<StockResponseDto> requestOrderByTop(String order) {
        String redisResult = redisTemplate.opsForValue().get("ranking_" + "top" + "_" + dateConverter.minuteFormatter(0));

        if (redisResult != null)
            return redisJsonManager.deserializeList(redisResult, StockResponseDto[].class);

        return stockCronService.saveTopRank(0);
    }

    private List<StockResponseDto> requestOrderByTrade(String order) {
        String redisResult = redisTemplate.opsForValue().get("ranking_" + "trade" + "_" + dateConverter.minuteFormatter(0));

        if (redisResult != null)
            return redisJsonManager.deserializeList(redisResult, StockResponseDto[].class);

        return stockCronService.saveTradeRank(0);
    }

    private List<StockResponseDto> requestOrderByCapital() {
        String redisResult = redisTemplate.opsForValue().get("ranking_" + "capital_" + dateConverter.minuteFormatter(0));

        if (redisResult != null)
            return redisJsonManager.deserializeList(redisResult, StockResponseDto[].class);

        return stockCronService.saveCapitalRank(0);
    }

    private List<StockResponseDto> requestOrderByChange() {
        String redisResult = redisTemplate.opsForValue().get("ranking_" + "change_" + dateConverter.minuteFormatter(0));

        if (redisResult != null)
            return redisJsonManager.deserializeList(redisResult, StockResponseDto[].class);

        return stockCronService.saveChangeRank(0);
    }

    public SkrrrCalculatorResponseDto calculateSkrrr(String code, SkrrrCalculatorRequestDto requestDto) {

        DateTimeFormatter originFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd");
        DateTimeFormatter newFormatter = DateTimeFormatter.ofPattern("yyyyMMdd");

        String date = requestDto.getDate();
        LocalDate dateInput = dateConverter.getLocalDate(date);
        LocalDate current = LocalDate.parse(dateConverter.getCurrentDate(), originFormatter);

        if (!isValidDate(date) || dateInput.isAfter(current))
            throw new StockException(DATE_INVALID);

        long calculatorMaxPrice = 9_999_999_999_999L;
        if (requestDto.getPrice() > calculatorMaxPrice)
            throw new StockException(CALCULATOR_PRICE_INVALID);

        LocalDate originDate = LocalDate.parse(date, originFormatter);

        String startDate = originDate.minusDays(5).format(newFormatter);
        String endDate = originDate.format(newFormatter);

        String publicParams = "?PRDT_TYPE_CD=300&PDNO=" + code;

        String publicUri = "/uapi/domestic-stock/v1/quotations/search-stock-info";
        Map publicResponse = authManager.getUserData(publicUri, publicParams, "CTPF1002R");

        Map<String, String> publicOutput = (Map<String, String>) publicResponse.get("output");

        String publicDate = publicOutput.get("scts_mket_lstg_dt");
        String privateDate = publicOutput.get("scts_mket_lstg_abol_dt");

        if (publicDate.compareTo(startDate) > 0 || !"".equals(privateDate))
            throw new StockException(STOCK_NOT_PUBLIC);

        String queryParams = "?fid_cond_mrkt_div_code=J" +
                "&fid_input_iscd=" + code +
                "&fid_input_date_1=" + startDate +
                "&fid_input_date_2=" + endDate +
                "&fid_period_div_code=D" +
                "&fid_org_adj_prc=0";

        String apiUri = "/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice";
        Map response = authManager.getUserData(apiUri, queryParams, "FHKST03010100");

        List<Map<String, String>> output = (List<Map<String, String>>) response.get("output2");

        String close = "stck_clpr";
        String previous = output.get(0).get(close);

        if (previous == null) {
            for (Map<String, String> map : output) {
                if (map.get(close) != null)
                    previous = map.get(close);
            }
        }

        if (previous == null)
            throw new StockException(STOCK_NOT_PUBLIC);

        if (requestDto.getPrice() < Integer.parseInt(previous))
            throw new StockException(STOCK_CANNOT_PURCHASE);

        Map<String, String> redisMap = getCurrentChangeChangeRate(code);
        if (redisMap == null)
            throw new StockException(CURRENT_NOT_FOUND);

        int currentPrice = Integer.parseInt(redisMap.get(REDIS_CURRENT_KEY));
        int previousPrice = Integer.parseInt(previous);
        long quantity = requestDto.getPrice() / previousPrice;

        long proceed = (currentPrice - previousPrice) * quantity;

        int candy = 500;
        int soul = 9_000;
        int chicken = 23_000;
        int iphone = 1_400_000;
        int slave = 9_860;

        return SkrrrCalculatorResponseDto.builder()
                .price(proceed)
                .candy(String.valueOf(proceed / candy))
                .soul(String.valueOf(proceed / soul))
                .chicken(String.valueOf(proceed / chicken))
                .iphone(String.valueOf(proceed / iphone))
                .slave(String.valueOf(proceed / slave))
                .build();
    }

    private boolean isValidDate(String date) {
        String[] dateInput = date.split("/");
        int year = Integer.parseInt(dateInput[0]);
        int month = Integer.parseInt(dateInput[1]);
        int day = Integer.parseInt(dateInput[2]);

        boolean isLeapYear = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0;
        int february = isLeapYear ? 29 : 28;

        int[] limitDate = {0, 31, february, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};

        return limitDate[month] >= day;
    }
}
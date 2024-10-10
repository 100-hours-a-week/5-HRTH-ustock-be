package com.hrth.ustock.service.main;

public interface StockServiceConst {
    // 코스피 & 코스닥
    String KOSPI_CODE = "0001";
    String KOSDAQ_CODE = "1001";
    String MARKET_CURRENT_PRICE = "bstp_nmix_prpr";
    String CHANGE_FROM_PREVIOUS_MARKET = "bstp_nmix_prdy_vrss";
    String CHANGE_RATE_FROM_PREVIOUS_MARKET = "bstp_nmix_prdy_ctrt";

    // 종목 순위
    String STOCK_NAME = "hts_kor_isnm";
    String STOCK_CODE = "mksc_shrn_iscd";
    String CHANGE_STOCK_CODE = "stck_shrn_iscd";

    // 종목 차트, 현재가, 전일 대비, 전일 대비율
    String STOCK_HIGH = "stck_hgpr";
    String STOCK_LOW = "stck_lwpr";
    String STOCK_OPEN = "stck_oprc";
    String STOCK_CLOSE = "stck_clpr";
    String STOCK_CURRENT_PRICE = "stck_prpr";
    String CHANGE_FROM_PREVIOUS_STOCK = "prdy_vrss";
    String CHANGE_RATE_FROM_PREVIOUS_STOCK = "prdy_ctrt";
    String STOCK_MARKET_HIGH = "stck_hgpr";
    String STOCK_MARKET_LOW = "stck_lwpr";
    String STOCK_MARKET_OPEN = "stck_oprc";
    String STOCK_MARKET_CLOSE = "stck_prpr";

    // redis 저장 키
    String REDIS_CURRENT_KEY = "current";
    String REDIS_CHANGE_KEY = "change";
    String REDIS_CHANGE_RATE_KEY = "changeRate";
    String REDIS_CHART_KEY = "chart";
    String REDIS_CHART_OPEN_KEY = "open";
    String REDIS_CHART_CLOSE_KEY = "close";
    String REDIS_CHART_HIGH_KEY = "high";
    String REDIS_CHART_LOW_KEY = "low";
    String REDIS_DATE_KEY = "date";

}

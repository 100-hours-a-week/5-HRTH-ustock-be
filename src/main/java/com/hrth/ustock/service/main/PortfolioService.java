package com.hrth.ustock.service.main;

import com.hrth.ustock.dto.main.holding.HoldingEmbedDto;
import com.hrth.ustock.dto.main.holding.HoldingRequestDto;
import com.hrth.ustock.dto.main.portfolio.PortfolioListDto;
import com.hrth.ustock.dto.main.portfolio.PortfolioRequestDto;
import com.hrth.ustock.dto.main.portfolio.PortfolioResponseDto;
import com.hrth.ustock.dto.main.portfolio.PortfolioUpdateDto;
import com.hrth.ustock.entity.main.User;
import com.hrth.ustock.entity.main.Holding;
import com.hrth.ustock.entity.main.Portfolio;
import com.hrth.ustock.entity.main.Stock;
import com.hrth.ustock.exception.domain.portfolio.PortfolioException;
import com.hrth.ustock.exception.domain.stock.StockException;
import com.hrth.ustock.exception.domain.user.UserException;
import com.hrth.ustock.repository.main.HoldingRepository;
import com.hrth.ustock.repository.main.PortfolioRepository;
import com.hrth.ustock.repository.main.StockRepository;
import com.hrth.ustock.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static com.hrth.ustock.exception.domain.portfolio.PortfolioExceptionType.*;
import static com.hrth.ustock.exception.domain.stock.StockExceptionType.STOCK_NOT_FOUND;
import static com.hrth.ustock.exception.domain.user.UserExceptionType.USER_NOT_FOUND;
import static com.hrth.ustock.service.main.StockServiceConst.REDIS_CURRENT_KEY;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioService {
    public static final int MAX_QUANTITY = 99_999;
    public static final long MAX_PRICE = 9_999_999_999L;

    private final PortfolioRepository portfolioRepository;
    private final UserRepository userRepository;
    private final HoldingRepository holdingRepository;
    private final StockRepository stockRepository;
    private final StockService stockService;

    @Transactional(readOnly = true)
    public PortfolioListDto getPortfolioList(Long userId) {

        PortfolioListDto portfolioListDto = new PortfolioListDto();
        List<Portfolio> list = portfolioRepository.findAllByUserUserId(userId);

        if (list == null || list.isEmpty())
            throw new PortfolioException(NO_PORTFOLIO);

        list.forEach(p -> {
            refreshPortfolio(p);
            portfolioListDto.setBudget(portfolioListDto.getBudget() + p.getBudget());
            portfolioListDto.setPrincipal(portfolioListDto.getPrincipal() + p.getPrincipal());
            portfolioListDto.setProfit(portfolioListDto.getProfit() + p.getProfit());
            portfolioListDto.getList().add(p.toPortfolioDto());
        });

        double profitRate = portfolioListDto.getPrincipal() == 0L ? 0.0 : (double) portfolioListDto.getProfit() / portfolioListDto.getPrincipal() * 100;
        portfolioListDto.setProfitRate(profitRate);

        return portfolioListDto;
    }

    @Transactional
    public PortfolioResponseDto getPortfolio(Long pfId) {

        PortfolioResponseDto portfolioResponseDto = new PortfolioResponseDto();
        Portfolio portfolio = portfolioRepository.findById(pfId)
                .orElseThrow(() -> new PortfolioException(PORTFOLIO_NOT_FOUND));

        List<Holding> holdings = portfolio.getHoldings();

        refreshPortfolio(portfolio);
        if (holdings == null || holdings.isEmpty()) {
            portfolioResponseDto.setName(portfolio.getName());
            return portfolioResponseDto;
        }

        for (Holding h : holdings) {
            Map<String, String> redisMap = stockService.getCurrentChangeChangeRate(h.getStock().getCode());

            if (redisMap == null) continue;

            int quantity = h.getQuantity();
            long average = h.getAverage();
            int current = Integer.parseInt(redisMap.get(REDIS_CURRENT_KEY));
            long profit = (long) quantity * current - (long) quantity * average;

            Stock stock = h.getStock();
            HoldingEmbedDto holdingEmbedDto = HoldingEmbedDto.builder()
                    .code(stock.getCode())
                    .name(stock.getName())
                    .logo(stock.getLogo())
                    .quantity(quantity)
                    .average(average)
                    .profitRate((quantity * average == 0) ? 0.0 : (double) profit / ((long) quantity * average) * 100)
                    .build();

            portfolioResponseDto.getStocks().add(holdingEmbedDto);
        }

        portfolioResponseDto.setName(portfolio.getName());
        portfolioResponseDto.setBudget(portfolio.getBudget());
        portfolioResponseDto.setPrincipal(portfolio.getPrincipal());
        portfolioResponseDto.setProfit(portfolio.getProfit());
        portfolioResponseDto.setProfitRate((portfolio.getPrincipal() == 0L) ? 0.0 : (double) portfolio.getProfit() / portfolio.getPrincipal() * 100);

        return portfolioResponseDto;
    }

    @Transactional
    public void addPortfolio(PortfolioRequestDto portfolioRequestDto, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserException(USER_NOT_FOUND));

        Portfolio portfolio = Portfolio.builder()
                .name(portfolioRequestDto.getName())
                .user(user)
                .budget(0L)
                .principal(0L)
                .profit(0L)
                .build();

        Portfolio findPortfolio = portfolioRepository.findByNameAndUserUserId(portfolio.getName(), userId).orElse(null);

        if (findPortfolio != null)
            throw new PortfolioException(PORTFOLIO_ALREADY_EXIST);

        portfolioRepository.save(portfolio);
    }

    @Transactional
    public void buyStock(Long pfId, String code, HoldingRequestDto holdingRequestDto) {
        Holding testHolding = holdingRepository.findHoldingByPortfolioIdAndStockCode(pfId, code).orElse(null);

        if (testHolding != null) {
            additionalBuyStock(pfId, code, holdingRequestDto);
            return;
        }

        int quantity = holdingRequestDto.getQuantity();
        long price = holdingRequestDto.getPrice();
        if (quantity > MAX_QUANTITY || price > MAX_PRICE)
            throw new PortfolioException(HOLDING_INPUT_INVALID);

        Portfolio portfolio = portfolioRepository.findById(pfId)
                .orElseThrow(() -> new PortfolioException(PORTFOLIO_NOT_FOUND));

        Stock stock = stockRepository.findByCode(code)
                .orElseThrow(() -> new StockException(STOCK_NOT_FOUND));

        Holding holding = Holding.builder()
                .portfolio(portfolio)
                .stock(stock)
                .average(price)
                .quantity(quantity)
                .user(portfolio.getUser())
                .build();

        holdingRepository.save(holding);
    }

    @Transactional
    public void additionalBuyStock(Long pfId, String code, HoldingRequestDto holdingRequestDto) {

        Holding target = holdingRepository.findHoldingByPortfolioIdAndStockCode(pfId, code)
                .orElseThrow(() -> new PortfolioException(HOLDING_NOT_FOUND));

        long price = holdingRequestDto.getPrice();
        int quantity = holdingRequestDto.getQuantity();

        if (0 > quantity || quantity > MAX_QUANTITY || 0 > price || price > MAX_PRICE)
            throw new PortfolioException(HOLDING_INPUT_INVALID);

        target.additionalBuyHolding(quantity, price);
    }

    @Transactional
    public void editHolding(Long pfId, String code, HoldingRequestDto holdingRequestDto) {

        Holding target = holdingRepository.findHoldingByPortfolioIdAndStockCode(pfId, code)
                .orElseThrow(() -> new PortfolioException(HOLDING_NOT_FOUND));

        int quantity = holdingRequestDto.getQuantity();
        long price = holdingRequestDto.getPrice();
        if (0 > quantity || quantity > MAX_QUANTITY || 0 > price || price > MAX_PRICE)
            throw new PortfolioException(HOLDING_INPUT_INVALID);

        target.updateHolding(quantity, price);
    }

    @Transactional
    public void deleteHolding(Long pfId, String code) {

        Portfolio portfolio = portfolioRepository.findById(pfId)
                .orElseThrow(() -> new PortfolioException(PORTFOLIO_NOT_FOUND));

        Holding target = holdingRepository.findHoldingByPortfolioIdAndStockCode(pfId, code)
                .orElseThrow(() -> new PortfolioException(HOLDING_NOT_FOUND));

        holdingRepository.delete(target);
        portfolio.getHoldings().remove(target);
    }

    @Transactional
    public void deletePortfolio(Long pfId) {

        Portfolio portfolio = portfolioRepository.findById(pfId)
                .orElseThrow(() -> new PortfolioException(PORTFOLIO_NOT_FOUND));

        List<Holding> list = portfolio.getHoldings();

        if (list != null && !list.isEmpty()) {
            holdingRepository.deleteAll(list);
        }

        portfolioRepository.delete(portfolio);
    }

    @Transactional
    protected void refreshPortfolio(Portfolio portfolio) {
        PortfolioUpdateDto portfolioUpdateDto = new PortfolioUpdateDto();
        List<Holding> list = portfolio.getHoldings();

        if (list == null || list.isEmpty()) {
            portfolio.updatePortfolio(portfolioUpdateDto);
            return;
        }

        // budget = 원금+수익, principal = 갯수+평단가, profit = 갯수+현재가
        for (Holding h : list) {
            Map<String, String> redisMap = stockService.getCurrentChangeChangeRate(h.getStock().getCode());

            if (redisMap == null) continue;

            int current = Integer.parseInt(redisMap.get(REDIS_CURRENT_KEY));

            long before = (long) h.getQuantity() * h.getAverage();
            long after = (long) h.getQuantity() * current;
            portfolioUpdateDto.setPrincipal(portfolioUpdateDto.getPrincipal() + before);
            portfolioUpdateDto.setProfit(portfolioUpdateDto.getProfit() + after - before);
        }

        portfolioUpdateDto.setBudget(portfolioUpdateDto.getPrincipal() + portfolioUpdateDto.getProfit());
        portfolio.updatePortfolio(portfolioUpdateDto);
        portfolioRepository.save(portfolio);
    }
}